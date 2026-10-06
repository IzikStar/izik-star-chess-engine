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
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import lab.EvolutionRunner;
import lab.RunSettings;
import lab.RunStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** One run playing. */
    private final class Job {
        final String file;
        final String name;
        final String startedAt = Instant.now().toString();
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
            this.file = file;
            this.name = name;
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

    LabJobs(LabApi lab, VariantStore variants) {
        this.lab = lab;
        this.variants = variants;
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
        app.get("/api/lab/algorithms", ctx -> json(ctx, algorithms()));
        app.get("/api/lab/job", ctx -> json(ctx, status()));
        app.post("/api/lab/runs", ctx -> json(ctx, start(JsonParser.parseString(ctx.body()).getAsJsonObject())));
        app.post("/api/lab/runs/{file}/resume", ctx -> json(ctx, resume(ctx.pathParam("file"))));
        app.post("/api/lab/runs/{file}/stop", ctx -> {
            JsonObject body = ctx.body().isBlank() ? new JsonObject() : JsonParser.parseString(ctx.body()).getAsJsonObject();
            json(ctx, stop(ctx.pathParam("file"), body.has("now") && body.get("now").getAsBoolean()));
        });
        app.delete("/api/lab/runs/{file}", ctx -> json(ctx, delete(ctx.pathParam("file"))));
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
        j.stop = true;
        if (now) {
            j.stopNow = true;
        }
        return status();
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

    private static void json(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
