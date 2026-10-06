package web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.Engine;
import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.SearchRequest;
import game.GameConfig;
import game.GameSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The web UI's server, driven the way the browser drives it: over the WebSocket, with JSON
 * (Phase 4c, docs/ui-research.md §7 step 1). No browser and no Stockfish involved.
 */
class WebServerTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final Engine NO_STOCKFISH = new Engine() {
        @Override public ChessMove bestMove(SearchRequest request) { return null; }
        @Override public boolean isAvailable() { return false; }
    };

    private WebServer server;
    private final List<Client> clients = new ArrayList<>();

    @AfterEach
    void stop() {
        clients.forEach(c -> c.ws.abort());
        if (server != null) {
            server.stop();
        }
    }

    private void startServer() {
        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute));
    }

    /** A WebSocket client that queues every state message it receives. */
    private final class Client implements WebSocket.Listener {
        final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        WebSocket ws;

        Client() throws Exception {
            ws = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/ws"), this)
                    .get(10, TimeUnit.SECONDS);
            clients.add(this);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(JsonParser.parseString(partial.toString()).getAsJsonObject());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        void send(String json) {
            ws.sendText(json, true).join();
        }

        void move(String uci) {
            send("{\"type\":\"move\",\"uci\":\"" + uci + "\"}");
        }

        /** The first message (skipping older ones) whose state matches; fails after {@link #WAIT}. */
        JsonObject await(Predicate<JsonObject> statePredicate) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (true) {
                long left = deadline - System.nanoTime();
                JsonObject msg = messages.poll(Math.max(0, left), TimeUnit.NANOSECONDS);
                if (msg == null) {
                    throw new AssertionError("no matching state within " + WAIT);
                }
                if (statePredicate.test(msg.getAsJsonObject("state"))) {
                    return msg;
                }
            }
        }

        /** The first message carrying an event of this kind. */
        JsonObject awaitEvent(String kind) throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (true) {
                JsonObject msg = messages.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (msg == null) {
                    throw new AssertionError("no " + kind + " event within " + WAIT);
                }
                if (hasEvent(msg, kind)) {
                    return msg;
                }
            }
        }

        JsonObject awaitPly(int plies) throws InterruptedException {
            return await(s -> s.getAsJsonArray("moves").size() == plies);
        }
    }

    private static void newGame(Client c, String mode, String color, int level) {
        c.send("{\"type\":\"newGame\",\"mode\":\"" + mode + "\",\"color\":\"" + color + "\",\"level\":" + level + "}");
    }

    private static boolean hasEvent(JsonObject msg, String kind) {
        for (JsonElement e : msg.getAsJsonArray("events")) {
            if (e.getAsJsonObject().get("kind").getAsString().equals(kind)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("A new connection gets the starting position with White's 20 legal moves")
    void snapshotOnConnect() throws Exception {
        startServer();
        Client c = new Client();
        JsonObject state = c.await(s -> true).getAsJsonObject("state");
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", state.get("fen").getAsString());
        assertEquals("white", state.get("turn").getAsString());
        assertTrue(state.get("humanTurn").getAsBoolean());
        assertEquals(20, state.getAsJsonArray("legalMoves").size());
        assertEquals("engine", state.getAsJsonObject("config").get("mode").getAsString());
        assertEquals(4, state.getAsJsonObject("config").get("level").getAsInt(), "the default is a real opponent, not random moves");
    }

    @Test
    @DisplayName("Two players: a whole game to checkmate over the socket (fool's mate)")
    void friendGameToMate() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "friend", "white", 1);
        c.await(s -> s.getAsJsonObject("config").get("mode").getAsString().equals("friend"));
        String[] game = {"f2f3", "e7e5", "g2g4", "d8h4"};
        JsonObject last = null;
        for (int i = 0; i < game.length; i++) {
            c.move(game[i]);
            last = c.awaitPly(i + 1);
        }
        // the mating move's message carries the gameOver event and the final state
        assertTrue(hasEvent(last, "gameOver"));
        JsonObject state = last.getAsJsonObject("state");
        assertEquals("CHECKMATE", state.get("status").getAsString());
        assertEquals("0-1", state.get("result").getAsString());
        assertEquals(0, state.getAsJsonArray("legalMoves").size());
        assertFalse(state.get("humanTurn").getAsBoolean());
        JsonArray moves = state.getAsJsonArray("moves");
        assertEquals("Qh4#", moves.get(3).getAsJsonObject().get("san").getAsString());
    }

    @Test
    @DisplayName("Against the engine: the human's move, then the engine thinking, then its reply")
    void engineReplies() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "engine", "white", 2);
        c.await(s -> s.getAsJsonObject("config").get("level").getAsInt() == 2);
        c.move("e2e4");
        JsonObject afterHuman = c.awaitPly(1).getAsJsonObject("state");
        assertTrue(afterHuman.get("engineThinking").getAsBoolean(), "engine starts right after the human's move");
        assertEquals(0, afterHuman.getAsJsonArray("legalMoves").size(), "no legal moves offered on the engine's turn");
        JsonObject reply = c.awaitPly(2);
        assertTrue(hasEvent(reply, "move"));
        JsonObject state = reply.getAsJsonObject("state");
        assertFalse(state.get("engineThinking").getAsBoolean());
        assertTrue(state.get("humanTurn").getAsBoolean());
        assertEquals("black", state.getAsJsonArray("moves").get(1).getAsJsonObject().get("color").getAsString());
    }

    @Test
    @DisplayName("Playing Black: the engine opens as White without being asked")
    void engineOpensForBlackHuman() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "engine", "black", 1);
        JsonObject state = c.awaitPly(1).getAsJsonObject("state");
        assertEquals("black", state.getAsJsonObject("config").get("humanColor").getAsString());
        assertEquals("black", state.get("turn").getAsString());
        assertTrue(state.get("humanTurn").getAsBoolean());
    }

    @Test
    @DisplayName("Watching the engine: each side keeps its own level")
    void engineVsEngineLevels() throws Exception {
        startServer();
        Client c = new Client();
        c.send("{\"type\":\"newGame\",\"mode\":\"computer\",\"level\":1,\"blackLevel\":3}");
        JsonObject config = c.await(s -> s.getAsJsonObject("config").get("mode").getAsString().equals("computer"))
                .getAsJsonObject("state").getAsJsonObject("config");
        assertEquals(1, config.get("level").getAsInt());
        assertEquals(3, config.get("blackLevel").getAsInt());
        c.awaitPly(2); // both sides move on their own
        newGame(c, "friend", "white", 1); // stop it
    }

    @Test
    @DisplayName("An illegal move is rejected and changes nothing")
    void illegalMoveRejected() throws Exception {
        startServer();
        Client c = new Client();
        c.await(s -> true);
        c.move("e2e5");
        JsonObject msg = c.awaitEvent("rejected");
        assertEquals(0, msg.getAsJsonObject("state").getAsJsonArray("moves").size());
    }

    @Test
    @DisplayName("Take-back in two-player mode removes one move")
    void undo() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "friend", "white", 1);
        c.move("e2e4");
        c.awaitPly(1);
        c.move("e7e5");
        c.awaitPly(2);
        c.send("{\"type\":\"undo\"}");
        JsonObject state = c.awaitPly(1).getAsJsonObject("state");
        assertEquals("black", state.get("turn").getAsString());
    }

    @Test
    @DisplayName("A hint arrives for the side to move and is cleared by the next move")
    void hint() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "friend", "white", 1);
        c.await(s -> s.getAsJsonObject("config").get("mode").getAsString().equals("friend"));
        c.send("{\"type\":\"hint\"}");
        JsonObject state = c.await(s -> !s.get("hint").isJsonNull()).getAsJsonObject("state");
        String hint = state.get("hint").getAsString();
        assertTrue(contains(state.getAsJsonArray("legalMoves"), hint), "hint " + hint + " is a legal move");
        c.move(hint);
        JsonObject after = c.awaitPly(1).getAsJsonObject("state");
        assertTrue(after.get("hint").isJsonNull());
    }

    @Test
    @DisplayName("Every connected browser sees the same game")
    void clientsShareTheGame() throws Exception {
        startServer();
        Client a = new Client();
        Client b = new Client();
        newGame(a, "friend", "white", 1);
        a.move("d2d4");
        JsonObject seenByB = b.awaitPly(1).getAsJsonObject("state");
        assertEquals("d4", seenByB.getAsJsonArray("moves").get(0).getAsJsonObject().get("san").getAsString());
    }

    @Test
    @DisplayName("Malformed messages are reported, not fatal")
    void malformed() throws Exception {
        startServer();
        Client c = new Client();
        c.await(s -> true);
        c.send("not json");
        c.awaitEvent("rejected");
        c.send("{\"type\":\"dance\"}");
        c.awaitEvent("rejected");
        c.move("e2e4"); // still working
        assertNotNull(c.awaitPly(1));
    }

    @Test
    @DisplayName("A timed game: the snapshot carries the clock; it starts with the first move")
    void timedGame() throws Exception {
        startServer();
        Client c = new Client();
        c.send("{\"type\":\"newGame\",\"mode\":\"friend\",\"time\":{\"initialMs\":180000,\"incrementMs\":2000}}");
        JsonObject clock = c.await(s -> !s.get("clock").isJsonNull()).getAsJsonObject("state").getAsJsonObject("clock");
        assertEquals(180000, clock.get("initialMs").getAsLong());
        assertEquals(2000, clock.get("incrementMs").getAsLong());
        assertEquals(180000, clock.get("white").getAsLong());
        assertTrue(clock.get("running").isJsonNull());
        c.move("e2e4");
        JsonObject after = c.awaitPly(1).getAsJsonObject("state");
        assertEquals("black", after.getAsJsonObject("clock").get("running").getAsString());
        assertTrue(after.get("pgn").getAsString().contains("[TimeControl \"180+2\"]"));
        newGame(c, "friend", "white", 1);
        assertTrue(c.await(s -> s.get("clock").isJsonNull()) != null, "a new game without time is untimed");
    }

    @Test
    @DisplayName("A game played over the socket can run out of time")
    void flagFallsOverTheSocket() throws Exception {
        startServer();
        Client c = new Client();
        c.send("{\"type\":\"newGame\",\"mode\":\"friend\",\"time\":{\"initialMs\":300}}");
        c.await(s -> !s.get("clock").isJsonNull());
        c.move("e2e4");
        JsonObject ended = c.awaitEvent("ended");
        JsonObject state = ended.getAsJsonObject("state");
        assertEquals("1-0", state.get("result").getAsString());
        assertEquals("TIMEOUT", state.getAsJsonObject("end").get("reason").getAsString());
        assertEquals("black", state.getAsJsonObject("end").get("side").getAsString());
        assertEquals(0, state.getAsJsonObject("clock").get("black").getAsLong());
        assertTrue(state.get("pgn").getAsString().contains("[Termination \"White won on time\"]"));
    }

    @Test
    @DisplayName("Resigning against the engine ends the game; the PGN says so")
    void resign() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "engine", "white", 1);
        JsonObject start = c.await(s -> s.get("canResign").getAsBoolean()).getAsJsonObject("state");
        assertTrue(start.get("canOfferDraw").getAsBoolean());
        c.send("{\"type\":\"resign\"}");
        JsonObject state = c.awaitEvent("ended").getAsJsonObject("state");
        assertEquals("0-1", state.get("result").getAsString());
        assertEquals("RESIGNATION", state.getAsJsonObject("end").get("reason").getAsString());
        assertFalse(state.get("canResign").getAsBoolean());
        assertTrue(state.get("pgn").getAsString().contains("[Termination \"Black won by resignation\"]"));
        assertTrue(state.get("pgn").getAsString().contains("[White \"Player\"]\n[Black \"Engine, level 1\"]"));
        c.send("{\"type\":\"resign\"}");
        assertEquals("nobody can resign now", c.awaitEvent("rejected").getAsJsonArray("events").get(0)
                .getAsJsonObject().get("reason").getAsString());
    }

    @Test
    @DisplayName("Two players: a draw offer is shown to both, and accepting it ends the game")
    void drawOfferBetweenFriends() throws Exception {
        startServer();
        Client c = new Client();
        newGame(c, "friend", "white", 1);
        c.await(s -> s.getAsJsonObject("config").get("mode").getAsString().equals("friend"));
        c.send("{\"type\":\"offerDraw\"}");
        JsonObject offered = c.awaitEvent("drawOffer").getAsJsonObject("state");
        assertEquals("white", offered.get("drawOffer").getAsString());
        c.send("{\"type\":\"answerDraw\",\"accept\":true}");
        JsonObject state = c.awaitEvent("ended").getAsJsonObject("state");
        assertEquals("1/2-1/2", state.get("result").getAsString());
        assertEquals("AGREEMENT", state.getAsJsonObject("end").get("reason").getAsString());
        assertTrue(state.get("drawOffer").isJsonNull());
    }

    @Test
    @DisplayName("Loading a PGN: a bad one is refused with the reason, a good one becomes a two-player game")
    void loadPgn() throws Exception {
        startServer();
        Client c = new Client();
        c.await(s -> true);
        c.send("{\"type\":\"loadPgn\",\"pgn\":\"1. e4 e5 2. Ke3\"}");
        JsonObject rejected = c.awaitEvent("rejected");
        assertEquals("Could not read the PGN: move 2. Ke3 is not a legal move",
                rejected.getAsJsonArray("events").get(0).getAsJsonObject().get("reason").getAsString());
        c.send("{\"type\":\"loadPgn\",\"pgn\":\"[White \\\"A\\\"]\\n1. f3 e5 2. g4 Qh4# 0-1\"}");
        JsonObject state = c.awaitPly(4).getAsJsonObject("state");
        assertEquals("friend", state.getAsJsonObject("config").get("mode").getAsString());
        assertEquals("CHECKMATE", state.get("status").getAsString());
        assertTrue(state.get("pgn").getAsString().endsWith("1. f3 e5 2. g4 Qh4# 0-1\n"));
    }

    @Test
    @DisplayName("Only --lan lets another device in: by default the server answers on this computer only")
    void lanAccess() throws Exception {
        List<String> addresses = WebServer.networkAddresses();
        Assumptions.assumeFalse(addresses.isEmpty(), "this machine has no network address");
        String fromPhone = "http://" + addresses.get(0) + ":%d/";
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        java.nio.file.Path runs = java.nio.file.Files.createTempDirectory("runs-test");
        java.nio.file.Path games = java.nio.file.Files.createTempDirectory("games-test");

        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute), runs, games);
        URI local = URI.create(String.format(fromPhone, server.port()));
        assertThrows(java.io.IOException.class, () -> http.send(HttpRequest.newBuilder(local).build(),
                HttpResponse.BodyHandlers.discarding()));
        server.stop();

        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute), runs, games,
                WebServer.ALL_INTERFACES);
        URI lan = URI.create(String.format(fromPhone, server.port()));
        assertEquals(200, http.send(HttpRequest.newBuilder(lan).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    @Test
    @DisplayName("Levels outside 0-13 are clamped to the ladder")
    void levels() {
        assertEquals(0, engine.Levels.clamp(-3));
        assertEquals(13, engine.Levels.clamp(99));
        assertEquals(7, engine.Levels.clamp(7));
        assertNull(GameStateJson.result(rules.GameStatus.CHECK, true));
    }

    @Test
    @DisplayName("Saved games: each move is saved, a left game stays unfinished, and it can be carried on")
    void savedGames() throws Exception {
        java.nio.file.Path games = java.nio.file.Files.createTempDirectory("games-test");
        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute), java.nio.file.Path.of("runs"), games);
        Client c = new Client();
        c.await(s -> true);
        c.send("{\"type\":\"newGame\",\"mode\":\"friend\",\"time\":{\"initialMs\":300000,\"incrementMs\":0}}");
        c.awaitEvent("reset");
        c.move("e2e4");
        c.move("e7e5");
        JsonObject state = c.awaitPly(2).getAsJsonObject("state");
        String id = state.get("savedId").getAsString();

        JsonArray list = get("/api/games").getAsJsonArray("games");
        assertEquals(1, list.size());
        JsonObject summary = list.get(0).getAsJsonObject();
        assertEquals(id, summary.get("id").getAsString());
        assertEquals("friend", summary.get("mode").getAsString());
        assertEquals(2, summary.get("plies").getAsInt());
        assertTrue(summary.get("result").isJsonNull());

        // a new game leaves this one unfinished; mate ends the next one and records the result
        newGame(c, "friend", "white", 4);
        c.awaitEvent("reset");
        for (String uci : new String[]{"f2f3", "e7e5", "g2g4", "d8h4"}) {
            c.move(uci);
        }
        String mated = c.awaitPly(4).getAsJsonObject("state").get("savedId").getAsString();
        assertFalse(id.equals(mated));
        JsonObject finished = get("/api/games/" + mated);
        assertEquals("0-1", finished.get("result").getAsString());
        assertEquals("Black won by checkmate", finished.get("termination").getAsString());
        assertEquals("Qh4#", finished.getAsJsonArray("moves").get(3).getAsJsonObject().get("san").getAsString());
        assertTrue(finished.get("pgn").getAsString().contains("2. g4 Qh4# 0-1"));
        assertEquals(2, get("/api/games").getAsJsonArray("games").size());

        // carrying on the first game: same moves, same mode, same clock, saved under the same id
        c.send("{\"type\":\"resumeGame\",\"id\":\"" + mated + "\"}");
        c.awaitEvent("rejected"); // it is over
        c.send("{\"type\":\"resumeGame\",\"id\":\"" + id + "\"}");
        state = c.await(s -> s.getAsJsonArray("moves").size() == 2 && id.equals(s.get("savedId").getAsString()))
                .getAsJsonObject("state");
        assertEquals("friend", state.getAsJsonObject("config").get("mode").getAsString());
        assertEquals(300000, state.getAsJsonObject("clock").get("initialMs").getAsInt());
        assertEquals("white", state.getAsJsonObject("clock").get("running").getAsString());
        c.move("g1f3");
        c.awaitPly(3);
        assertEquals(3, get("/api/games/" + id).get("plies").getAsInt());

        // a take-back to no moves removes an empty game; deleting works over HTTP
        newGame(c, "friend", "white", 4);
        c.awaitEvent("reset");
        c.move("d2d4");
        String empty = c.awaitPly(1).getAsJsonObject("state").get("savedId").getAsString();
        c.send("{\"type\":\"undo\"}");
        c.awaitPly(0);
        assertEquals(404, status("GET", "/api/games/" + empty));
        assertEquals(204, status("DELETE", "/api/games/" + mated));
        assertEquals(1, get("/api/games").getAsJsonArray("games").size());
    }

    @Test
    @DisplayName("Variants: a new game names one, its rules hold on the socket, and the PGN says which")
    void variantGame() throws Exception {
        startServer();
        Client c = new Client();
        c.await(s -> true);
        c.send("{\"type\":\"newGame\",\"mode\":\"friend\",\"variant\":\"antichess\"}");
        JsonObject state = c.await(s -> s.getAsJsonObject("variant").get("id").getAsString().equals("antichess"))
                .getAsJsonObject("state");
        assertEquals("Antichess", state.getAsJsonObject("variant").get("name").getAsString());
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", state.get("fen").getAsString());
        c.move("e2e3");
        c.move("b7b5");
        state = c.awaitPly(2).getAsJsonObject("state");
        // a capture is there, so it is the only move
        assertEquals(1, state.getAsJsonArray("legalMoves").size());
        assertEquals("f1b5", state.getAsJsonArray("legalMoves").get(0).getAsString());
        assertTrue(state.get("pgn").getAsString().contains("[Variant \"Antichess\"]"), state.get("pgn").getAsString());

        // Stockfish plays chess only, so another variant's engine levels stop at the built-in engine's top
        c.send("{\"type\":\"newGame\",\"mode\":\"engine\",\"color\":\"black\",\"level\":12,\"variant\":\"king-of-the-hill\"}");
        state = c.awaitPly(1).getAsJsonObject("state");
        assertEquals("king-of-the-hill", state.getAsJsonObject("variant").get("id").getAsString());
        assertEquals(engine.Levels.STOCKFISH_FROM - 1, state.getAsJsonObject("config").get("level").getAsInt());

        c.send("{\"type\":\"newGame\",\"variant\":\"no-such-game\"}");
        assertTrue(c.awaitEvent("rejected").toString().contains("unknown variant"));
        // a champion of any game can be played in that game; the Lab's browser test plays an antichess one
    }

    @Test
    @DisplayName("Saved games keep their variant, and a carried-on game goes on by its rules")
    void savedVariantGame() throws Exception {
        java.nio.file.Path games = java.nio.file.Files.createTempDirectory("games-test");
        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute), java.nio.file.Path.of("runs"), games);
        Client c = new Client();
        c.await(s -> true);
        c.send("{\"type\":\"newGame\",\"mode\":\"friend\",\"variant\":\"three-check\"}");
        c.awaitEvent("reset");
        c.move("e2e4");
        c.move("f7f6");
        c.move("d1h5"); // check one
        String id = c.awaitPly(3).getAsJsonObject("state").get("savedId").getAsString();
        JsonObject saved = get("/api/games/" + id);
        assertEquals("three-check", saved.get("variant").getAsString());
        assertEquals("Qh5+", saved.getAsJsonArray("moves").get(2).getAsJsonObject().get("san").getAsString());

        newGame(c, "friend", "white", 1);
        c.awaitEvent("reset");
        c.send("{\"type\":\"resumeGame\",\"id\":\"" + id + "\"}");
        JsonObject state = c.await(s -> s.getAsJsonArray("moves").size() == 3 && id.equals(s.get("savedId").getAsString()))
                .getAsJsonObject("state");
        assertEquals("three-check", state.getAsJsonObject("variant").get("id").getAsString());
        assertTrue(state.get("fen").getAsString().contains(" 2+3 "), state.get("fen").getAsString());
    }

    @Test
    @DisplayName("A variant the player made: saved over HTTP, played over the socket, kept in its saved games")
    void madeVariant() throws Exception {
        java.nio.file.Path games = java.nio.file.Files.createTempDirectory("games-test");
        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute), java.nio.file.Path.of("runs"), games, "127.0.0.1");
        String amazon = ai.variant.VariantJson.write(ai.variant.TestVariants.AMAZON_CHESS);
        assertEquals(400, send("PUT", "/api/variants/other-id", amazon).statusCode());
        assertEquals(400, send("PUT", "/api/variants/amazon-chess", amazon.replace("RNBAKBNR", "RNBQKBNR")).statusCode());
        HttpResponse<String> saved = send("PUT", "/api/variants/amazon-chess", amazon);
        assertEquals(200, saved.statusCode(), saved.body());
        JsonArray list = get("/api/variants").getAsJsonArray("variants");
        assertEquals("amazon-chess", list.get(list.size() - 1).getAsJsonObject().get("id").getAsString());
        assertEquals("RBN", get("/api/variants/amazon-chess").getAsJsonArray("pieces").get(1).getAsJsonObject()
                .get("betza").getAsString());
        HttpResponse<String> betza = send("POST", "/api/betza", "{\"text\":\"fmWcfF\"}");
        assertEquals(200, betza.statusCode(), betza.body());
        assertEquals(2, JsonParser.parseString(betza.body()).getAsJsonObject().getAsJsonArray("atoms").size());
        assertEquals(400, send("POST", "/api/betza", "{\"text\":\"X\"}").statusCode());

        Client c = new Client();
        c.await(s -> true);
        c.send("{\"type\":\"newGame\",\"mode\":\"friend\",\"variant\":\"amazon-chess\"}");
        JsonObject state = c.await(s -> s.getAsJsonObject("variant").get("id").getAsString().equals("amazon-chess"))
                .getAsJsonObject("state");
        assertTrue(state.getAsJsonObject("variant").get("custom").getAsBoolean());
        // the board shows an invented piece's moves under the mouse: the state says how it moves
        JsonArray statePieces = state.getAsJsonObject("variant").getAsJsonArray("pieces");
        for (var e : statePieces) {
            JsonObject p = e.getAsJsonObject();
            assertEquals(p.get("letter").getAsString().equals("A"), p.get("invented").getAsBoolean(), p.toString());
            assertTrue(p.getAsJsonArray("atoms").size() > 0, p.toString());
        }
        // a picture for the Amazon
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        HttpResponse<String> put = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.port() + "/api/variants/amazon-chess/art/A/w"))
                .header("Content-Type", "image/png").PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, put.statusCode(), put.body());
        assertTrue(get("/api/variants/amazon-chess").getAsJsonObject("art").getAsJsonObject("A").has("w"));
        HttpResponse<byte[]> pic = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + server.port() + "/api/variants/amazon-chess/art/A/w")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pic.statusCode());
        assertEquals("image/png", pic.headers().firstValue("Content-Type").orElse("").split(";")[0]);
        assertTrue(java.util.Arrays.equals(png, pic.body()));
        assertEquals(404, send("GET", "/api/variants/amazon-chess/art/A/b", "").statusCode());
        assertEquals(400, send("PUT", "/api/variants/amazon-chess/art/A/w", "<html>").statusCode());
        c.move("e2e4");
        c.move("e7e5");
        c.move("d1f3"); // the Amazon's knight jump
        String id = c.awaitPly(3).getAsJsonObject("state").get("savedId").getAsString();

        // deleting the variant leaves its games playable: each keeps its own copy
        assertEquals(204, send("DELETE", "/api/variants/amazon-chess", "").statusCode());
        assertEquals(400, send("DELETE", "/api/variants/chess", "").statusCode());
        JsonObject game = get("/api/games/" + id);
        assertEquals("Amazon chess", game.get("variantName").getAsString());
        assertEquals("Af3", game.getAsJsonArray("moves").get(2).getAsJsonObject().get("san").getAsString());
        newGame(c, "friend", "white", 1);
        c.awaitEvent("reset");
        c.send("{\"type\":\"resumeGame\",\"id\":\"" + id + "\"}");
        state = c.await(s -> s.getAsJsonArray("moves").size() == 3 && id.equals(s.get("savedId").getAsString()))
                .getAsJsonObject("state");
        assertEquals("amazon-chess", state.getAsJsonObject("variant").get("id").getAsString());
        assertTrue(state.get("pgn").getAsString().contains("[Variant \"Amazon chess\"]"));
    }

    @Test
    @DisplayName("The health check plays a variant, saved or not, against itself and reports")
    void healthCheck() throws Exception {
        server = WebServer.start(0, hub -> new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), NO_STOCKFISH), hub::execute));
        assertFalse(get("/api/health").get("running").getAsBoolean());
        String amazon = ai.variant.VariantJson.write(ai.variant.TestVariants.AMAZON_CHESS);
        assertEquals(400, send("POST", "/api/health", "{\"games\":4,\"depth\":1}").statusCode());
        assertEquals(400, send("POST", "/api/health", "{\"games\":0,\"depth\":1,\"variant\":" + amazon + "}").statusCode());
        assertEquals(400, send("POST", "/api/health", "{\"games\":4,\"depth\":6,\"variant\":" + amazon + "}").statusCode());
        if (!arena.FairyStockfish.installed()) {
            assertEquals(400, send("POST", "/api/health", "{\"games\":4,\"engine\":\"fairy-stockfish\",\"variant\":"
                    + amazon + "}").statusCode());
        }
        HttpResponse<String> started = send("POST", "/api/health", "{\"games\":4,\"depth\":1,\"blackDepth\":2,"
                + "\"maxPlies\":80,\"randomPlies\":2,\"variety\":0,\"threads\":2,\"seed\":5,\"variant\":" + amazon + "}");
        assertEquals(200, started.statusCode(), started.body());
        JsonObject state = get("/api/health");
        for (int i = 0; i < 600 && state.get("running").getAsBoolean(); i++) {
            Thread.sleep(100);
            state = get("/api/health");
        }
        assertEquals("amazon-chess", state.get("variantId").getAsString());
        assertEquals(4, state.get("done").getAsInt());
        JsonObject report = state.getAsJsonObject("report");
        assertEquals(4, report.get("whiteWins").getAsInt() + report.get("blackWins").getAsInt() + report.get("draws").getAsInt());
        assertTrue(report.get("movesPerTurn").getAsDouble() > 10, report.toString());
        JsonObject settings = state.getAsJsonObject("settings");
        assertEquals(1, settings.get("whiteDepth").getAsInt());
        assertEquals(2, settings.get("blackDepth").getAsInt());
        assertEquals(80, settings.get("maxPlies").getAsInt());
        assertEquals("built-in", settings.get("engine").getAsString());
        JsonObject details = report.getAsJsonObject("details");
        assertEquals(4, details.getAsJsonObject("lengths").getAsJsonObject("all").get("count").getAsInt());
        assertTrue(details.getAsJsonObject("results").getAsJsonObject("whiteWins").has("low"), details.toString());
        boolean amazonCounted = false;
        for (JsonElement piece : details.getAsJsonArray("pieces")) {
            amazonCounted |= piece.getAsJsonObject().get("letter").getAsString().equals("A");
        }
        assertTrue(amazonCounted, details.toString());
        assertTrue(details.getAsJsonArray("samples").size() > 0);
        assertTrue(state.has("fairyInstalled"));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject get(String path) throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + server.port() + path)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private int status(String method, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static boolean contains(JsonArray array, String value) {
        for (JsonElement e : array) {
            if (e.getAsString().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
