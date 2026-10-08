package web;

import ai.eval.ChessEvaluate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.Engine;
import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.Weights;
import engine.SearchRequest;
import evolution.RandomMutationExample;
import game.GameConfig;
import game.GameSession;
import io.javalin.http.NotFoundResponse;
import lab.EvolutionRunner;
import lab.HallOfFame;
import lab.RunSettings;
import lab.RunStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.ChessMove;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lab page's API over a real (tiny) evolution run, and playing its champion. */
class LabApiTest {

    @TempDir
    static Path dir;
    private static LabApi lab;

    @BeforeAll
    static void recordARun() {
        try (RunStore store = RunStore.open(dir.resolve("tiny.db"))) {
            EvolutionRunner.start(store, "Tiny run", new RandomMutationExample(4, 0.3, 0.05),
                    new RunSettings(2, 1, 1, 2, 80, 2, 3, 1, 2), EvolutionRunner.Listener.SILENT, () -> false);
        }
        lab = new LabApi(dir);
    }

    @Test
    @DisplayName("Lists the runs in the folder")
    void listsRuns() throws Exception {
        JsonObject runs = lab.runs();
        assertEquals(1, runs.getAsJsonArray("runs").size());
        JsonObject run = runs.getAsJsonArray("runs").get(0).getAsJsonObject();
        assertEquals("tiny.db", run.get("file").getAsString());
        assertEquals("Tiny run", run.get("name").getAsString());
        assertEquals(2, run.get("generationsDone").getAsInt());
        assertTrue(run.has("lastYardstick"));
    }

    @Test
    @DisplayName("A run has its generations and the weights its champions moved")
    void runDetail() {
        JsonObject run = lab.run("tiny.db");
        assertEquals(2, run.getAsJsonArray("generations").size());
        assertEquals(12, run.getAsJsonArray("generations").get(0).getAsJsonObject().get("games").getAsInt());
        run.getAsJsonArray("weights").forEach(w -> {
            JsonObject o = w.getAsJsonObject();
            assertEquals(2, o.getAsJsonArray("values").size()); // one value per generation's champion
        });
    }

    @Test
    @DisplayName("A generation lists its games, and each replays move by move")
    void gamesReplay() {
        JsonObject generation = lab.generation("tiny.db", 0);
        int games = generation.getAsJsonArray("games").size();
        assertEquals(12 + 4, games); // 6 pairings × 2 colours, then 2 yardstick openings × 2
        assertEquals("yardstick", generation.getAsJsonArray("games").get(games - 1).getAsJsonObject().get("kind").getAsString());
        JsonObject game = lab.game("tiny.db", 0, 0);
        int plies = generation.getAsJsonArray("games").get(0).getAsJsonObject().get("plies").getAsInt();
        assertEquals(plies, game.getAsJsonArray("moves").size());
        assertTrue(game.getAsJsonArray("moves").get(0).getAsJsonObject().has("fenAfter"));
    }

    @Test
    @DisplayName("The opening tree branches the population's games move by move")
    void openingTree() {
        JsonObject root = lab.tree("tiny.db", java.util.List.of(), 0, Integer.MAX_VALUE, java.util.List.of());
        assertEquals(24, root.getAsJsonObject("tally").get("games").getAsInt()); // 2 generations × 12
        com.google.gson.JsonArray children = root.getAsJsonArray("children");
        int sum = 0;
        for (var c : children) {
            sum += c.getAsJsonObject().getAsJsonObject("tally").get("games").getAsInt();
        }
        assertEquals(24, sum);
        JsonObject first = children.get(0).getAsJsonObject();
        assertEquals(first.getAsJsonObject("tally").get("games").getAsInt(), first.get("forced").getAsInt()); // the suite's moves
        String uci = first.get("uci").getAsString();
        JsonObject next = lab.tree("tiny.db", java.util.List.of(uci), 1, 1, java.util.List.of("population", "yardstick"));
        assertEquals(first.get("san").getAsString(), next.getAsJsonArray("line").get(0).getAsJsonObject().get("san").getAsString());
        assertEquals(1, next.get("firstGeneration").getAsInt());
        assertThrows(io.javalin.http.BadRequestResponse.class,
                () -> lab.tree("tiny.db", java.util.List.of("e2e5"), 0, 9, java.util.List.of()));
        assertThrows(io.javalin.http.BadRequestResponse.class,
                () -> lab.tree("tiny.db", java.util.List.of(), 0, 9, java.util.List.of("bogus")));
    }

    @Test
    @DisplayName("Only run files in the folder can be read")
    void rejectsOtherFiles() {
        assertThrows(NotFoundResponse.class, () -> lab.run("../tiny.db"));
        assertThrows(NotFoundResponse.class, () -> lab.run("missing.db"));
        assertThrows(NotFoundResponse.class, () -> lab.game("tiny.db", 0, 999));
    }

    @Test
    @DisplayName("A new game can be played against a champion, the next against the usual engine, with the weights picked")
    void playTheChampion() throws Exception {
        Engine noStockfish = new Engine() {
            @Override public ChessMove bestMove(SearchRequest request) { return null; }
            @Override public boolean isAvailable() { return false; }
        };
        GameHub hub = new GameHub();
        MinimaxEngine builtIn = new MinimaxEngine();
        hub.useBuiltIn(builtIn);
        hub.useLab(lab);
        hub.attach(new GameSession(GameConfig.defaults(), new EngineSelector(builtIn, noStockfish), hub::execute));
        BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        GameHub.Client client = json -> messages.add(JsonParser.parseString(json).getAsJsonObject());
        hub.connect(client);
        try {
            hub.onMessage(client, "{\"type\":\"newGame\",\"mode\":\"engine\",\"color\":\"white\",\"level\":9,"
                    + "\"champion\":{\"run\":\"tiny.db\",\"generation\":1}}");
            JsonObject state = until(messages, s -> s.has("opponent") && !s.get("opponent").isJsonNull());
            JsonObject opponent = state.getAsJsonObject("opponent");
            assertEquals("Champion of Tiny run, generation 1", opponent.get("label").getAsString());
            assertEquals(8, state.getAsJsonObject("config").get("level").getAsInt()); // capped: no Stockfish
            assertEquals(lab.champion("tiny.db", 1).params(), builtIn.evaluator().params());

            hub.onMessage(client, "{\"type\":\"newGame\",\"mode\":\"engine\",\"color\":\"white\",\"level\":3}");
            JsonObject usual = until(messages, s -> s.get("opponent").isJsonNull());
            assertEquals("tuned", usual.get("weights").getAsString()); // the app's default
            assertSame(Weights.TUNED.evaluator(), builtIn.evaluator());

            // the classic weights, picked in the New game dialog, stay until another pick
            hub.onMessage(client, "{\"type\":\"newGame\",\"mode\":\"engine\",\"color\":\"white\",\"level\":3,"
                    + "\"weights\":\"classic\"}");
            until(messages, s -> s.get("weights").getAsString().equals("classic"));
            assertEquals(ChessEvaluate.CLASSIC.params(), builtIn.evaluator().params());
            hub.onMessage(client, "{\"type\":\"newGame\",\"mode\":\"engine\",\"color\":\"black\",\"level\":3}");
            JsonObject again = until(messages, s -> s.getAsJsonObject("config").get("humanColor").getAsString().equals("black"));
            assertEquals("classic", again.get("weights").getAsString());
            assertSame(Weights.CLASSIC.evaluator(), builtIn.evaluator());
        } finally {
            hub.shutdown();
        }
    }

    private static JsonObject until(BlockingQueue<JsonObject> messages, java.util.function.Predicate<JsonObject> test)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            JsonObject msg = messages.poll(1, TimeUnit.SECONDS);
            if (msg != null && test.test(msg.getAsJsonObject("state"))) {
                return msg.getAsJsonObject("state");
            }
        }
        throw new AssertionError("no such state");
    }

    @Test
    @DisplayName("The champion lookup refuses an unfinished generation")
    void unfinishedGeneration() {
        assertThrows(IllegalArgumentException.class, () -> lab.champion("tiny.db", 5));
        assertNotNull(lab.champion("tiny.db", 0));
    }

    @Test
    @DisplayName("The hall of fame holds the run's last champion; a champion can be kept by hand and played")
    void hallOfFame() throws Exception {
        JsonObject fame = lab.fame();
        String last = "Tiny-run-g1-m"
                + lab.run("tiny.db").getAsJsonArray("generations").get(1).getAsJsonObject().get("champion").getAsInt();
        boolean found = false;
        for (var e : fame.getAsJsonArray("entries")) {
            found |= e.getAsJsonObject().get("name").getAsString().equals(last);
        }
        assertTrue(found, last);

        JsonObject body = JsonParser.parseString("{\"member\": 2, \"name\": \"my-pick\", \"note\": \"plays nice endgames\"}")
                .getAsJsonObject();
        assertEquals("my-pick", lab.keep("tiny.db", 0, body).get("name").getAsString());
        HallOfFame.Entry entry = lab.hall().get("my-pick").orElseThrow();
        assertEquals("plays nice endgames", entry.reason());
        assertEquals(entry.params(), lab.champion("hof:my-pick", 0).params());
        assertEquals("chess", lab.champion("hof:my-pick", 0).variant().id());
        assertTrue(entry.pgn().contains("[White \"#2\"]") || entry.pgn().contains("[Black \"#2\"]"));
        assertTrue(lab.runPgn("tiny.db").startsWith("[Event \"Tiny run\"]"));
    }
}
