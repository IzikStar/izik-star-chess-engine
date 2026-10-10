package web;

import ai.variant.Variant;
import ai.variant.VariantJson;
import arena.GameRecord;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evolution.Evolution;
import evolution.Option;
import game.VariantStore;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.NotFoundResponse;
import lab.EvolutionRunner;
import lab.HallOfFame;
import lab.RunSettings;
import lab.RunStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Starting, stopping and resuming evolution runs from the Lab page (roadmap stage 2). One run plays
 * at a time, on its own threads inside the game server; it is recorded exactly as {@code lab.Cli}
 * records a run, so the two can be mixed and a run started here can be resumed there.
 *
 * <pre>
 * GET    /api/lab/algorithms             the algorithms, each with its settings ({@link Option})
 * GET    /api/lab/job                    what is running: {running, file, name, generation, gamesDone,
 *                                        gamesPlanned, startedAt, stopping, error?, lastFile?}
 * POST   /api/lab/runs                   {name, algorithm, variant, settings{...}, options{...}}: starts a
 *                                        run in a new file; 400 {error} when the settings cannot be played
 * POST   /api/lab/runs/{file}/resume     continues a stopped run
 * POST   /api/lab/runs/{file}/stop       {now}: stops the run after this generation, or at once
 * DELETE /api/lab/runs/{file}            deletes a run that is not running
 * </pre>
 *
 * <p>A run can also play on a worker: the owner's PC, started with {@code IZIKSTAR_SERVER} set (see
 * {@link lab.Worker}). {@code "where": "worker"} on a start or a resume queues the run instead of
 * playing it here; the run's file is still made here, so this server stays the one place every run
 * lives. The worker asks for the job, plays it in a folder of its own and sends the run file back
 * after each generation:
 *
 * <pre>
 * GET  /api/lab/worker/claim?worker=W              the queued run for worker W: {file, name}, or {}
 * GET  /api/lab/runs/{file}/download               the run file, for the worker to continue
 * GET  /api/lab/fame/{name}/file                   a hall of fame entry's file (a yardstick)
 * POST /api/lab/worker/{file}/progress?worker=W    {generation, gamesDone, gamesPlanned}: answers {stop, stopNow}
 * PUT  /api/lab/worker/{file}/snapshot?worker=W    the run file so far, gzipped; replaces the copy here
 * PUT  /api/lab/worker/{file}/fame/{name}?worker=W a hall of fame entry the run kept
 * POST /api/lab/worker/{file}/done?worker=W        {error?}: the worker stopped playing the run
 * </pre>
 *
 * Only the worker that claimed a run may send its progress and files (409 otherwise). A worker
 * that has not been heard from for {@link #STALE_AFTER} is stale: stopping its run ends the job at
 * once, and another worker may claim it. The queued job survives a restart of this server in
 * {@code runs/.worker-job.json}.
 */
final class LabJobs {

    /** What the Lab says about each algorithm, in a line. */
    static final Map<String, String> ABOUT = Map.of(
            "evolution.FromZero", "A genetic algorithm with every choice a setting: start from nothing, random or"
                    + " the defaults; survivors, immigrants, tournament selection, crossover and a mutation step"
                    + " that shrinks over the run. Plays any game.",
            "evolution.MaterialExperiment", "The Material 1 design: 16 mutants of the tuned and classic chess"
                    + " weights, best three survive, eight children a generation, every member plays Stockfish."
                    + " Chess only.",
            "evolution.RandomMutationExample", "The naive example from the guide: keep the better half, refill"
                    + " with mutated copies. For checking the plumbing.");

    /** A worker silent this long is taken to be gone. */
    static final java.time.Duration STALE_AFTER = java.time.Duration.ofMinutes(10);
    /** Where a run queued for a worker is remembered, in the runs folder. */
    static final String WORKER_JOB = ".worker-job.json";

    /** One run playing, here or on a worker. */
    private final class Job {
        final String file;
        final String name;
        final String startedAt;
        /** Plays on a worker rather than here. */
        final boolean remote;
        /** The worker that claimed it, or null while it waits for one. */
        volatile String worker;
        /** When the worker last called, in milliseconds. */
        volatile long lastSeen;
        volatile int generation = -1;
        volatile int gamesDone;
        volatile int gamesPlanned;
        volatile boolean stop;
        volatile boolean stopNow;
        volatile boolean finished;
        volatile String error;

        /** Opened when the first generation starts (the run is in its file) or the job ends. */
        final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);

        Job(String file, String name) {
            this(file, name, false, Instant.now().toString());
        }

        Job(String file, String name, boolean remote, String startedAt) {
            this.file = file;
            this.name = name;
            this.remote = remote;
            this.startedAt = startedAt;
        }

        boolean stale() {
            return remote && worker != null && clock.getAsLong() - lastSeen > STALE_AFTER.toMillis();
        }

        EvolutionRunner.Listener listener() {
            return new EvolutionRunner.Listener() {
                @Override
                public void generationStarted(int number, int games) {
                    generation = number;
                    gamesPlanned = games;
                    gamesDone = 0;
                    started.countDown();
                }

                @Override
                public void game(int number, GameRecord game) {
                    gamesDone++;
                }
            };
        }
    }

    private final LabApi lab;
    private final VariantStore variants;
    private volatile Job job;
    /** The copy that plays a run file when it is another copy's run synced here, else null. */
    private volatile Function<String, String> replicaOf = file -> null;

    /** The time in milliseconds; tests move it on. */
    private final java.util.function.LongSupplier clock;

    LabJobs(LabApi lab, VariantStore variants) {
        this(lab, variants, System::currentTimeMillis);
    }

    LabJobs(LabApi lab, VariantStore variants, java.util.function.LongSupplier clock) {
        this.lab = lab;
        this.variants = variants;
        this.clock = clock;
        job = loadWorkerJob();
    }

    /** Tells which run files are other copies' runs (cloud.CloudSync), so they are not resumed or deleted here. */
    void useReplicas(Function<String, String> replicaOf) {
        this.replicaOf = replicaOf;
    }

    private void refuseReplica(String file, String what) {
        String origin = replicaOf.apply(file);
        if (origin != null) {
            throw new BadRequestResponse("this run is played on " + origin + "; " + what + " it there");
        }
    }

    void routes(Javalin app) {
        app.get("/api/lab/algorithms", ctx -> Json.send(ctx, algorithms()));
        app.get("/api/lab/job", ctx -> Json.send(ctx, status()));
        app.post("/api/lab/runs", ctx -> Json.send(ctx, start(JsonParser.parseString(ctx.body()).getAsJsonObject())));
        app.post("/api/lab/runs/{file}/resume", ctx -> Json.send(ctx, resume(ctx.pathParam("file"),
                !ctx.body().isBlank() && onWorker(JsonParser.parseString(ctx.body()).getAsJsonObject()))));
        app.post("/api/lab/runs/{file}/stop", ctx -> {
            JsonObject body = ctx.body().isBlank() ? new JsonObject() : JsonParser.parseString(ctx.body()).getAsJsonObject();
            Json.send(ctx, stop(ctx.pathParam("file"), body.has("now") && body.get("now").getAsBoolean()));
        });
        app.delete("/api/lab/runs/{file}", ctx -> Json.send(ctx, delete(ctx.pathParam("file"))));

        app.get("/api/lab/worker/claim", ctx -> Json.send(ctx, claim(worker(ctx))));
        app.get("/api/lab/runs/{file}/download", ctx -> {
            String file = ctx.pathParam("file");
            Path path = lab.dir().resolve(file);
            if (!LabApi.isRunFile(file) || !Files.isRegularFile(path)) {
                throw new NotFoundResponse("no run file " + file);
            }
            ctx.header("Content-Disposition", "attachment; filename=\"" + file + "\"")
                    .contentType("application/vnd.sqlite3").result(Files.newInputStream(path));
        });
        app.get("/api/lab/fame/{name}/file", ctx -> {
            String name = ctx.pathParam("name");
            if (lab.hall().get(name).isEmpty()) {
                throw new NotFoundResponse("no hall of fame entry " + name);
            }
            ctx.contentType("application/json").result(Files.readString(lab.hall().dir().resolve(name + ".json")));
        });
        app.post("/api/lab/worker/{file}/progress", ctx -> {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            Json.send(ctx, progress(ctx.pathParam("file"), worker(ctx), body.get("generation").getAsInt(),
                    body.get("gamesDone").getAsInt(), body.get("gamesPlanned").getAsInt()));
        });
        app.put("/api/lab/worker/{file}/snapshot", ctx -> {
            try (InputStream in = new GZIPInputStream(ctx.bodyInputStream())) {
                Json.send(ctx, snapshot(ctx.pathParam("file"), worker(ctx), in));
            }
        });
        app.put("/api/lab/worker/{file}/fame/{name}", ctx ->
                Json.send(ctx, fame(ctx.pathParam("file"), worker(ctx), ctx.pathParam("name"), ctx.body())));
        app.post("/api/lab/worker/{file}/done", ctx -> {
            JsonObject body = ctx.body().isBlank() ? new JsonObject() : JsonParser.parseString(ctx.body()).getAsJsonObject();
            Json.send(ctx, done(ctx.pathParam("file"), worker(ctx),
                    body.has("error") && !body.get("error").isJsonNull() ? body.get("error").getAsString() : null));
        });
    }

    private static String worker(io.javalin.http.Context ctx) {
        String w = ctx.queryParam("worker");
        if (w == null || w.isBlank()) {
            throw new BadRequestResponse("say which worker: ?worker=NAME");
        }
        return w.trim();
    }

    static JsonObject algorithms() {
        JsonArray list = new JsonArray();
        for (String className : EvolutionRunner.algorithms()) {
            Evolution e = EvolutionRunner.algorithm(className);
            JsonObject o = new JsonObject();
            o.addProperty("className", className);
            o.addProperty("name", className.replaceFirst("^.*\\.", ""));
            o.addProperty("about", ABOUT.getOrDefault(className, ""));
            o.addProperty("chessOnly", className.equals("evolution.MaterialExperiment"));
            JsonArray options = new JsonArray();
            for (Option opt : e.options()) {
                JsonObject j = new JsonObject();
                j.addProperty("key", opt.key());
                j.addProperty("label", opt.label());
                j.addProperty("help", opt.help());
                j.addProperty("default", opt.defaultValue());
                if (opt.isNumber()) {
                    j.addProperty("min", opt.min());
                    j.addProperty("max", opt.max());
                } else {
                    JsonArray choices = new JsonArray();
                    opt.choices().forEach(choices::add);
                    j.add("choices", choices);
                    j.addProperty("multiple", opt.multiple());
                }
                options.add(j);
            }
            o.add("options", options);
            list.add(o);
        }
        JsonObject out = new JsonObject();
        out.add("algorithms", list);
        return out;
    }

    JsonObject status() {
        JsonObject o = new JsonObject();
        Job j = job;
        o.addProperty("running", j != null && !j.finished);
        if (j == null) {
            return o;
        }
        o.addProperty("file", j.file);
        o.addProperty("name", j.name);
        o.addProperty("generation", j.generation);
        o.addProperty("gamesDone", j.gamesDone);
        o.addProperty("gamesPlanned", j.gamesPlanned);
        o.addProperty("startedAt", j.startedAt);
        o.addProperty("stopping", j.stop || j.stopNow);
        o.addProperty("finished", j.finished);
        o.addProperty("where", j.remote ? "worker" : "server");
        if (j.remote) {
            if (j.worker != null) {
                o.addProperty("worker", j.worker);
            }
            o.addProperty("stale", j.stale());
        }
        if (j.error != null) {
            o.addProperty("error", j.error);
        }
        return o;
    }

    /** True while a run plays. */
    boolean running() {
        Job j = job;
        return j != null && !j.finished;
    }

    /** The file of the run playing, or null. */
    String runningFile() {
        Job j = job;
        return j != null && !j.finished ? j.file : null;
    }

    synchronized JsonObject start(JsonObject body) {
        if (running()) {
            throw new BadRequestResponse("a run is already playing: stop it first");
        }
        String name = body.has("name") && !body.get("name").getAsString().isBlank()
                ? body.get("name").getAsString().trim() : "Run " + Instant.now().toString().substring(0, 16).replace('T', ' ');
        String algorithm = body.has("algorithm") ? body.get("algorithm").getAsString() : "evolution.FromZero";
        if (!EvolutionRunner.algorithms().contains(algorithm)) {
            throw new BadRequestResponse("unknown algorithm " + algorithm);
        }
        Evolution evolution;
        RunSettings settings;
        try {
            evolution = EvolutionRunner.algorithm(algorithm);
            settings = EvolutionRunner.check(settings(body));
            Evolution.resolve(evolution.options(), settings.algorithmOptions());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestResponse(String.valueOf(e.getMessage()));
        }
        String file = freeFile(name);
        if (onWorker(body)) {
            try (RunStore store = RunStore.open(lab.dir().resolve(file))) {
                // as EvolutionRunner.start records it, so the worker only ever resumes
                store.createRun(name, algorithm, EvolutionRunner.check(settings.withAlgorithmOptions(
                        Evolution.resolve(evolution.options(), settings.algorithmOptions()))));
            }
            queue(file, name);
            JsonObject out = new JsonObject();
            out.addProperty("file", file);
            return out;
        }
        Job j = new Job(file, name);
        job = j;
        Thread thread = new Thread(() -> {
            try (RunStore store = RunStore.open(lab.dir().resolve(file))) {
                EvolutionRunner.start(store, name, evolution, settings, j.listener(), () -> j.stop, () -> j.stopNow);
            } catch (RuntimeException e) {
                j.error = String.valueOf(e.getMessage());
            } finally {
                j.finished = true;
                j.started.countDown();
            }
        }, "lab-run");
        thread.setDaemon(true);
        thread.start();
        awaitRunRow(j);
        JsonObject out = new JsonObject();
        out.addProperty("file", file);
        return out;
    }

    /**
     * Waits (up to 10 s) until the run's first generation starts, by when its file holds the run,
     * so the page that opens it next finds it: the runner writes it on its own thread, and a cold
     * start used to answer "no run in ..." first.
     */
    private static void awaitRunRow(Job j) {
        try {
            j.started.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The run's settings: the defaults, then whatever {@code body.settings} says, plus the game and the algorithm's options. */
    private RunSettings settings(JsonObject body) {
        JsonObject tree = JsonParser.parseString(RunSettings.defaults().toJson()).getAsJsonObject();
        // the defaults play Stockfish; a run from the page asks for it explicitly
        JsonArray none = new JsonArray();
        none.add("default");
        tree.add("yardsticks", none);
        tree.addProperty("stockfishFrom", 0);
        if (body.has("settings")) {
            for (Map.Entry<String, JsonElement> e : body.getAsJsonObject("settings").entrySet()) {
                if (e.getValue().isJsonNull()) {
                    continue;
                }
                tree.add(e.getKey(), e.getValue());
            }
        }
        String variantId = body.has("variant") ? body.get("variant").getAsString() : "chess";
        Variant variant = variants.byId(variantId).orElseThrow(() -> new BadRequestResponse("unknown variant " + variantId));
        tree.addProperty("variant", variant.id());
        tree.remove("variantDef");
        if (!VariantStore.isBuiltIn(variant.id())) {
            tree.add("variantDef", VariantJson.toTree(variant));
        }
        boolean suite = !tree.has("openingPlies") || tree.get("openingPlies").getAsInt() == 0;
        if (!variant.id().equals("chess") && suite) {
            tree.addProperty("openingPlies", 4); // a variant has no opening book
        }
        Map<String, String> options = new LinkedHashMap<>();
        if (body.has("options")) {
            for (Map.Entry<String, JsonElement> e : body.getAsJsonObject("options").entrySet()) {
                options.put(e.getKey(), e.getValue().getAsString());
            }
        }
        JsonObject opts = new JsonObject();
        options.forEach(opts::addProperty);
        tree.add("algorithmOptions", opts);
        return RunSettings.fromJson(tree.toString());
    }

    /** A new file named after the run that does not exist yet. */
    private String freeFile(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "run";
        }
        for (int i = 1; ; i++) {
            String file = (i == 1 ? base : base + "-" + i) + ".db";
            if (!Files.exists(lab.dir().resolve(file))) {
                return file;
            }
        }
    }

    synchronized JsonObject resume(String file) {
        return resume(file, false);
    }

    /** Continues a stopped run, here or (with {@code onWorker}) on the next worker that asks. */
    synchronized JsonObject resume(String file, boolean onWorker) {
        if (running()) {
            throw new BadRequestResponse("a run is already playing: stop it first");
        }
        refuseReplica(file, "resume");
        String algorithm;
        String name;
        try (RunStore store = lab.open(file)) {
            RunStore.RunRow run = store.run().orElseThrow(() -> new NotFoundResponse("no run in " + file));
            if (store.generations().size() >= run.settings().generations()) {
                throw new BadRequestResponse("that run is finished");
            }
            algorithm = run.algorithm();
            name = run.name();
        }
        Evolution evolution;
        try {
            evolution = EvolutionRunner.algorithm(algorithm);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("the run's algorithm " + algorithm + " is not in this program");
        }
        if (onWorker) {
            queue(file, name);
            return status();
        }
        Job j = new Job(file, name);
        job = j;
        Thread thread = new Thread(() -> {
            try (RunStore store = lab.open(file)) {
                EvolutionRunner.resume(store, evolution, j.listener(), () -> j.stop, () -> j.stopNow);
            } catch (RuntimeException e) {
                j.error = String.valueOf(e.getMessage());
            } finally {
                j.finished = true;
                j.started.countDown();
            }
        }, "lab-run");
        thread.setDaemon(true);
        thread.start();
        return status();
    }

    JsonObject stop(String file, boolean now) {
        Job j = job;
        if (j == null || j.finished || !j.file.equals(file)) {
            throw new BadRequestResponse("that run is not playing");
        }
        if (j.remote && (j.worker == null || j.stale())) {
            finish(j, null); // nobody is playing it: there is nothing to wait for
            return status();
        }
        j.stop = true;
        if (now) {
            j.stopNow = true;
        }
        return status();
    }

    // ---- runs on a worker ----------------------------------------------------

    private static boolean onWorker(JsonObject body) {
        return body.has("where") && body.get("where").getAsString().equals("worker");
    }

    /** Queues the run in {@code file} for a worker. */
    private void queue(String file, String name) {
        Job j = new Job(file, name, true, Instant.now().toString());
        job = j;
        saveWorkerJob(j);
    }

    /** The run waiting for {@code worker}: one no worker has, this worker's own, or a stale worker's. */
    synchronized JsonObject claim(String worker) {
        JsonObject out = new JsonObject();
        Job j = job;
        if (j == null || j.finished || !j.remote) {
            return out;
        }
        if (j.worker != null && !j.worker.equals(worker) && !j.stale()) {
            return out;
        }
        j.worker = worker;
        j.lastSeen = clock.getAsLong();
        saveWorkerJob(j);
        out.addProperty("file", j.file);
        out.addProperty("name", j.name);
        return out;
    }

    /** The worker's own job in {@code file}, else 409. */
    private Job claimedBy(String file, String worker) {
        Job j = job;
        if (j == null || j.finished || !j.remote || !j.file.equals(file) || !worker.equals(j.worker)) {
            throw new ConflictResponse("this worker is not playing " + file);
        }
        j.lastSeen = clock.getAsLong();
        return j;
    }

    JsonObject progress(String file, String worker, int generation, int gamesDone, int gamesPlanned) {
        Job j = claimedBy(file, worker);
        j.generation = generation;
        j.gamesDone = gamesDone;
        j.gamesPlanned = gamesPlanned;
        JsonObject out = new JsonObject();
        out.addProperty("stop", j.stop);
        out.addProperty("stopNow", j.stopNow);
        return out;
    }

    /**
     * Replaces this server's copy of the run with the worker's: written beside it, checked to hold
     * the same run, then moved over it in one step, so the page never reads half a file.
     */
    synchronized JsonObject snapshot(String file, String worker, InputStream in) throws IOException {
        Job j = claimedBy(file, worker);
        Path target = lab.dir().resolve(file);
        Path upload = lab.dir().resolve("." + file + ".upload");
        try {
            Files.copy(in, upload, StandardCopyOption.REPLACE_EXISTING);
            int generations;
            try (RunStore store = RunStore.open(upload)) {
                RunStore.RunRow run = store.run().orElseThrow(() -> new BadRequestResponse("the upload holds no run"));
                if (!run.name().equals(j.name)) {
                    throw new BadRequestResponse("the upload holds the run " + run.name() + ", not " + j.name);
                }
                generations = store.generations().size();
            } catch (IllegalStateException e) {
                throw new BadRequestResponse("the upload is not a run file");
            }
            Files.move(upload, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            JsonObject out = new JsonObject();
            out.addProperty("generations", generations);
            return out;
        } finally {
            Files.deleteIfExists(upload);
            Files.deleteIfExists(lab.dir().resolve("." + file + ".upload-wal"));
            Files.deleteIfExists(lab.dir().resolve("." + file + ".upload-shm"));
        }
    }

    /**
     * Keeps a hall of fame entry the worker's run kept. An entry of that name from another run is
     * never replaced; one from this run is (the runner adds reasons to its own entries).
     */
    synchronized JsonObject fame(String file, String worker, String name, String json) throws IOException {
        claimedBy(file, worker);
        lab.hall().get(name).ifPresent(old -> {
            if (!file.equals(old.run())) {
                throw new ConflictResponse("the hall of fame already has " + name + " from another run");
            }
        });
        Path dir = lab.hall().dir();
        Files.createDirectories(dir);
        Path upload = dir.resolve("." + name + ".upload");
        try {
            Files.writeString(upload, json);
            HallOfFame.Entry entry = HallOfFame.readFile(upload);
            if (!entry.name().equals(name) || !file.equals(entry.run())) {
                throw new BadRequestResponse("the entry is not " + name + " from " + file);
            }
            Files.move(upload, dir.resolve(name + ".json"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (RuntimeException e) {
            if (e instanceof io.javalin.http.HttpResponseException) {
                throw e;
            }
            throw new BadRequestResponse("not a hall of fame entry: " + e.getMessage());
        } finally {
            Files.deleteIfExists(upload);
        }
        JsonObject out = new JsonObject();
        out.addProperty("name", name);
        return out;
    }

    synchronized JsonObject done(String file, String worker, String error) {
        finish(claimedBy(file, worker), error);
        return status();
    }

    private void finish(Job j, String error) {
        j.error = error;
        j.finished = true;
        try {
            Files.deleteIfExists(lab.dir().resolve(WORKER_JOB));
        } catch (IOException e) {
            // the job is over either way; a stale file names a finished run and is dropped on load
        }
    }

    private void saveWorkerJob(Job j) {
        JsonObject o = new JsonObject();
        o.addProperty("file", j.file);
        o.addProperty("name", j.name);
        o.addProperty("startedAt", j.startedAt);
        if (j.worker != null) {
            o.addProperty("worker", j.worker);
        }
        try {
            Files.createDirectories(lab.dir());
            Files.writeString(lab.dir().resolve(WORKER_JOB), o.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The run queued for a worker before this server last stopped, if its file is still here. */
    private Job loadWorkerJob() {
        Path path = lab.dir().resolve(WORKER_JOB);
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            JsonObject o = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String file = o.get("file").getAsString();
            if (!LabApi.isRunFile(file) || !Files.isRegularFile(lab.dir().resolve(file))) {
                return null;
            }
            Job j = new Job(file, o.get("name").getAsString(), true, o.get("startedAt").getAsString());
            if (o.has("worker")) {
                j.worker = o.get("worker").getAsString();
                j.lastSeen = clock.getAsLong(); // give it time to call again
            }
            return j;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    synchronized JsonObject delete(String file) {
        refuseReplica(file, "delete");
        if (!LabApi.isRunFile(file)) {
            throw new NotFoundResponse("no run file " + file);
        }
        if (file.equals(runningFile())) {
            throw new BadRequestResponse("that run is playing: stop it first");
        }
        Path path = lab.dir().resolve(file);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundResponse("no run file " + file);
        }
        try {
            Files.delete(path);
            Files.deleteIfExists(lab.dir().resolve(file + "-wal"));
            Files.deleteIfExists(lab.dir().resolve(file + "-shm"));
        } catch (IOException e) {
            throw new BadRequestResponse("cannot delete " + file + ": " + e.getMessage());
        }
        JsonObject out = new JsonObject();
        out.addProperty("deleted", file);
        return out;
    }

}
