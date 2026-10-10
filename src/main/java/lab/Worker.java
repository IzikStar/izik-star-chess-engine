package lab;

import arena.GameRecord;
import com.google.gson.JsonObject;
import evolution.Evolution;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Plays the server's Lab runs on this computer, so a run started from the server's page ("on my
 * PC") gets this computer's cores while its file lives on the server only. It asks the server for
 * a queued run, copies the run file and the hall of fame entries it plays against into a folder of
 * its own, resumes the run there, and after every generation sends the run file and any entry the
 * run kept back to the server. Stopping the run on the page reaches it through its progress calls.
 *
 * <p>The server's protocol is in {@code web.LabJobs}; the link to it, from the environment, in
 * {@link ServerLink}.
 */
public final class Worker {

    /** How long to wait between asks while the server has nothing to play. */
    static final long IDLE_MS = 15_000;
    /** How often the run's progress goes to the server while it plays. */
    static final long PROGRESS_MS = 10_000;

    private final ServerLink server;
    private final Path work;
    private final java.util.function.Consumer<String> log;

    /** A worker for {@code server} that keeps its runs under {@code work} while they play. */
    public Worker(ServerLink server, Path work, java.util.function.Consumer<String> log) {
        this.server = server;
        this.work = work;
        this.log = log;
    }

    /** Asks for runs and plays them, until the thread is interrupted. */
    public void loop() {
        log.accept("Lab worker \"" + server.worker() + "\": waiting for runs from " + server.base());
        boolean warned = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!playNext()) {
                    Thread.sleep(IDLE_MS);
                }
                warned = false;
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                if (!warned) {
                    log.accept("Lab worker: cannot reach the server (" + e.getMessage() + "); trying again");
                    warned = true;
                }
                try {
                    Thread.sleep(IDLE_MS);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    /** Plays the run the server has queued, if any; false when there is none. */
    public boolean playNext() throws IOException {
        JsonObject claim = server.getJson("api/lab/worker/claim", true);
        if (!claim.has("file")) {
            return false;
        }
        play(claim.get("file").getAsString(), claim.get("name").getAsString());
        return true;
    }

    private void play(String file, String name) throws IOException {
        log.accept("Lab worker: playing \"" + name + "\" (" + file + ")");
        Files.createDirectories(work);
        Path dir = Files.createTempDirectory(work, "run-");
        Path local = dir.resolve(file);
        server.download("api/lab/runs/" + file + "/download", local);
        Path fame = dir.resolve(HallOfFame.FOLDER);
        Files.createDirectories(fame);

        AtomicBoolean stop = new AtomicBoolean();
        AtomicBoolean stopNow = new AtomicBoolean();
        int[] progress = {-1, 0, 0}; // generation, games done, games planned
        String error = null;
        boolean sent = false;
        Map<Path, Long> known = new HashMap<>();
        ScheduledExecutorService beat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lab-worker-progress");
            t.setDaemon(true);
            return t;
        });
        try (RunStore store = RunStore.open(local)) {
            RunStore.RunRow run = store.run().orElseThrow(() -> new IllegalStateException("no run in " + file));
            for (String y : run.settings().yardsticks()) {
                if (y.startsWith("hof:")) {
                    String entry = y.substring(4);
                    server.download("api/lab/fame/" + entry + "/file", fame.resolve(entry + ".json"));
                }
            }
            remember(fame, known);
            Evolution evolution = EvolutionRunner.algorithm(run.algorithm());

            beat.scheduleAtFixedRate(() -> {
                JsonObject body = new JsonObject();
                synchronized (progress) {
                    body.addProperty("generation", progress[0]);
                    body.addProperty("gamesDone", progress[1]);
                    body.addProperty("gamesPlanned", progress[2]);
                }
                try {
                    JsonObject answer = server.postJson("api/lab/worker/" + file + "/progress", body);
                    stop.compareAndSet(false, answer.has("stop") && answer.get("stop").getAsBoolean());
                    stopNow.compareAndSet(false, answer.has("stopNow") && answer.get("stopNow").getAsBoolean());
                } catch (ServerLink.Refused e) {
                    if (e.status() == 409) { // the run is no longer this worker's
                        log.accept("Lab worker: the server took \"" + name + "\" back; stopping");
                        stopNow.set(true);
                    }
                } catch (IOException e) {
                    // the next call tries again; the run goes on meanwhile
                }
            }, 0, PROGRESS_MS, TimeUnit.MILLISECONDS);

            EvolutionRunner.Listener listener = new EvolutionRunner.Listener() {
                @Override
                public void generationStarted(int generation, int games) {
                    synchronized (progress) {
                        progress[0] = generation;
                        progress[1] = 0;
                        progress[2] = games;
                    }
                }

                @Override
                public void game(int generation, GameRecord game) {
                    synchronized (progress) {
                        progress[1]++;
                    }
                }

                @Override
                public void generation(RunStore.GenerationRow row) {
                    log.accept("Lab worker: \"" + name + "\" generation " + row.number() + " done");
                    if (!send(store, dir, file, fame, known)) {
                        log.accept("Lab worker: could not send generation " + row.number() + "; the next one will");
                    }
                }
            };
            EvolutionRunner.resume(store, evolution, listener, stop::get, stopNow::get);
            sent = sendFinal(store, dir, file, fame, known);
        } catch (RuntimeException e) {
            error = String.valueOf(e.getMessage());
            log.accept("Lab worker: \"" + name + "\" failed: " + error);
        } finally {
            beat.shutdownNow();
        }
        JsonObject body = new JsonObject();
        if (error != null) {
            body.addProperty("error", error);
        } else if (!sent) {
            body.addProperty("error", "the worker could not send the last generations; they are on "
                    + server.worker() + " in " + dir);
        }
        try {
            server.postJson("api/lab/worker/" + file + "/done", body);
        } catch (ServerLink.Refused e) {
            // the server already ended the job (stopped while this worker was away)
        }
        if (sent) {
            delete(dir);
        }
        log.accept("Lab worker: \"" + name + "\" " + (error == null ? "stopped" : "ended") + "; waiting for runs");
    }

    /** The last copy must arrive: tries a few times, a minute apart. */
    private boolean sendFinal(RunStore store, Path dir, String file, Path fame, Map<Path, Long> known) {
        for (int attempt = 0; attempt < 10; attempt++) {
            if (send(store, dir, file, fame, known)) {
                return true;
            }
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Sends the run file and the hall of fame entries new or changed since the last time; false if any failed. */
    private boolean send(RunStore store, Path dir, String file, Path fame, Map<Path, Long> known) {
        Path copy = dir.resolve("snapshot.db");
        Path gz = dir.resolve("snapshot.db.gz");
        try {
            Files.deleteIfExists(copy);
            store.snapshot(copy);
            try (InputStream in = Files.newInputStream(copy);
                 OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
                in.transferTo(out);
            }
            server.put("api/lab/worker/" + file + "/snapshot", gz, "application/gzip");
            for (Path entry : changed(fame, known)) {
                String name = entry.getFileName().toString().replaceFirst("\\.json$", "");
                server.put("api/lab/worker/" + file + "/fame/" + name, entry, "application/json");
                known.put(entry, Files.getLastModifiedTime(entry).toMillis());
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.accept("Lab worker: sending to the server failed: " + e.getMessage());
            return false;
        } finally {
            try {
                Files.deleteIfExists(copy);
                Files.deleteIfExists(gz);
            } catch (IOException e) {
                // left in the run's own folder, which goes when the run does
            }
        }
    }

    private static void remember(Path fame, Map<Path, Long> known) throws IOException {
        for (Path p : list(fame)) {
            known.put(p, Files.getLastModifiedTime(p).toMillis());
        }
    }

    private static java.util.List<Path> changed(Path fame, Map<Path, Long> known) throws IOException {
        java.util.List<Path> out = new java.util.ArrayList<>();
        for (Path p : list(fame)) {
            Long seen = known.get(p);
            if (seen == null || seen != Files.getLastModifiedTime(p).toMillis()) {
                out.add(p);
            }
        }
        return out;
    }

    private static java.util.List<Path> list(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    private static void delete(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            // a leftover folder under the work folder; harmless
        }
    }
}
