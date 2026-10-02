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
        assertEquals(1, state.getAsJsonObject("config").get("level").getAsInt());
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
    @DisplayName("The game's sounds are served over HTTP")
    void serveSounds() throws Exception {
        startServer();
        HttpResponse<byte[]> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + server.port() + "/sounds/moveSound1.wav")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().length > 1000);
    }

    @Test
    @DisplayName("UI levels 1-10 map to the session's skill levels 0-18 and back")
    void levels() {
        for (int ui = 1; ui <= 10; ui++) {
            assertEquals(ui, GameStateJson.uiLevel(GameStateJson.skillLevel(ui)));
        }
        assertEquals(0, GameStateJson.skillLevel(1));
        assertEquals(18, GameStateJson.skillLevel(10));
        assertNull(GameStateJson.result(rules.GameStatus.CHECK, true));
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
