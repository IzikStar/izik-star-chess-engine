package web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.Engine;
import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.SearchRequest;
import game.GameConfig;
import game.GameSession;
import game.VariantStore;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import lab.HallOfFame;
import lab.RunStore;
import lab.ServerLink;
import lab.Worker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.ChessMove;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs queued on the server and played by a worker (the owner's PC), whose files come back to the server. */
class LabWorkerTest {

    private static final Engine NO_STOCKFISH = new Engine() {
        @Override public ChessMove bestMove(SearchRequest request) { return null; }
        @Override public boolean isAvailable() { return false; }
    };

    private static final String RUN = "{\"name\": \"On my PC\", \"algorithm\": \"evolution.FromZero\","
            + " \"variant\": \"antichess\", \"where\": \"worker\", \"settings\": {\"generations\": 3, \"depth\": 1,"
            + " \"openingsPerPairing\": 1, \"threads\": 2, \"yardstickEvery\": 1, \"yardstickOpenings\": 1,"
            + " \"yardsticks\": [\"zero\"], \"deepDepth\": 0, \"maxPlies\": 60}, \"options\": {\"population\": \"4\"}}";

    @Test
    @DisplayName("A run started 'on my PC' is made on the server, played by the worker, and every generation and kept champion ends up on the server")
    void endToEnd(@TempDir Path dir) throws Exception {
        Path runs = dir.resolve("runs");
        WebServer server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute), runs, dir.resolve("games"));
        try {
            URI base = URI.create("http://127.0.0.1:" + server.port() + "/");
            HttpClient http = HttpClient.newHttpClient();
            HttpResponse<String> started = http.send(HttpRequest.newBuilder(base.resolve("api/lab/runs"))
                    .POST(HttpRequest.BodyPublishers.ofString(RUN)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, started.statusCode(), started.body());
            String file = JsonParser.parseString(started.body()).getAsJsonObject().get("file").getAsString();
            assertTrue(Files.isRegularFile(runs.resolve(file)), "the run lives on the server from the start");

            JsonObject job = get(http, base.resolve("api/lab/job"));
            assertTrue(job.get("running").getAsBoolean());
            assertEquals("worker", job.get("where").getAsString());
            assertFalse(job.has("worker"), "no worker has it yet");

            List<String> log = new ArrayList<>();
            Worker worker = new Worker(new ServerLink(base, null, null, "test-pc"), dir.resolve("pc"), log::add);
            assertTrue(worker.playNext(), String.join("\n", log));
            assertFalse(worker.playNext(), "nothing more to play");

            job = get(http, base.resolve("api/lab/job"));
            assertFalse(job.get("running").getAsBoolean(), job.toString());
            assertFalse(job.has("error"), job.toString());
            assertEquals("test-pc", job.get("worker").getAsString());
            try (RunStore store = RunStore.open(runs.resolve(file))) {
                assertEquals(3, store.generations().size());
            }
            List<HallOfFame.Entry> kept = new HallOfFame(runs.resolve(HallOfFame.FOLDER)).list();
            assertTrue(kept.stream().anyMatch(e -> file.equals(e.run()) && e.generation() == 2), kept.toString());
            assertFalse(Files.exists(runs.resolve(LabJobs.WORKER_JOB)));
            try (var left = Files.list(dir.resolve("pc"))) {
                assertEquals(0, left.count(), "the worker cleans up after a run it sent");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("Only the worker that claimed a run sends it; a silent worker goes stale, and its run can be stopped or claimed")
    void claims(@TempDir Path dir) throws Exception {
        AtomicLong now = new AtomicLong(1_000_000);
        LabApi lab = new LabApi(dir.resolve("runs"));
        LabJobs jobs = new LabJobs(lab, new VariantStore(dir.resolve("variants")), now::get);
        String file = jobs.start(JsonParser.parseString(RUN).getAsJsonObject()).get("file").getAsString();
        assertTrue(jobs.running());
        assertThrows(BadRequestResponse.class, () -> jobs.start(JsonParser.parseString(RUN).getAsJsonObject()),
                "one run at a time");

        assertEquals(file, jobs.claim("pc").get("file").getAsString());
        assertEquals(file, jobs.claim("pc").get("file").getAsString(), "the same worker again (it restarted)");
        assertFalse(jobs.claim("laptop").has("file"), "taken");
        assertThrows(ConflictResponse.class, () -> jobs.progress(file, "laptop", 0, 1, 10));
        assertThrows(ConflictResponse.class, () -> jobs.snapshot(file, "laptop", new ByteArrayInputStream(new byte[0])));
        assertThrows(BadRequestResponse.class, () -> jobs.snapshot(file, "pc", new ByteArrayInputStream("junk".getBytes())));
        assertTrue(Files.isRegularFile(lab.dir().resolve(file)), "a bad upload leaves the run as it was");

        JsonObject answer = jobs.progress(file, "pc", 0, 5, 10);
        assertFalse(answer.get("stop").getAsBoolean());
        assertEquals(5, jobs.status().get("gamesDone").getAsInt());

        // the server restarts: the queued run and its worker are remembered
        LabJobs again = new LabJobs(lab, new VariantStore(dir.resolve("variants")), now::get);
        assertTrue(again.running());
        assertEquals("pc", again.status().get("worker").getAsString());

        jobs.stop(file, false);
        assertTrue(jobs.progress(file, "pc", 1, 0, 10).get("stop").getAsBoolean(), "the stop reaches the worker");
        assertTrue(jobs.running(), "it plays on until the worker says it stopped");

        now.addAndGet(LabJobs.STALE_AFTER.toMillis() + 1);
        assertTrue(jobs.status().get("stale").getAsBoolean());
        assertEquals(file, jobs.claim("laptop").get("file").getAsString(), "a stale worker's run can be claimed");
        assertThrows(ConflictResponse.class, () -> jobs.progress(file, "pc", 1, 0, 10));
        jobs.done(file, "laptop", null);
        assertFalse(jobs.running());
        assertFalse(Files.exists(lab.dir().resolve(LabJobs.WORKER_JOB)));

        // a queued run nobody has claimed stops at once
        jobs.resume(file, true);
        assertTrue(jobs.running());
        jobs.stop(file, false);
        assertFalse(jobs.running());
    }

    @Test
    @DisplayName("The server link comes from the environment and never shows its password")
    void link() {
        assertTrue(ServerLink.fromEnv(Map.of()).isEmpty());
        ServerLink link = ServerLink.fromEnv(Map.of(ServerLink.SERVER, "https://example.org", ServerLink.USER, "izik",
                ServerLink.PASSWORD, "secret", ServerLink.WORKER_NAME, "home-pc")).orElseThrow();
        assertEquals("https://example.org/", link.base().toString());
        assertEquals("home-pc", link.worker());
        assertFalse(link.toString().contains("secret"), link.toString());
    }

    private static JsonObject get(HttpClient http, URI uri) throws Exception {
        return JsonParser.parseString(http.send(HttpRequest.newBuilder(uri).build(),
                HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }
}
