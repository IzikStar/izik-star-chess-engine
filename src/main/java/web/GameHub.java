package web;

import ai.BitBoard.BitBoardEvaluate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.EngineSelector;
import engine.MinimaxEngine;
import game.GameConfig;
import game.GameListener;
import game.GameSession;
import game.GameEnd;
import game.TimeControl;
import rules.ChessMove;
import rules.MoveResult;
import rules.Pgn;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Connects one {@link GameSession} to any number of browser clients.
 *
 * <p>Threading: the session's dispatcher is a single "game" thread owned by the hub. Browser
 * commands are queued onto it, and so are the session's own engine results, so session state is
 * only touched there (the rule GameSession documents). Each task on that thread collects the
 * session's events and, when it finishes, sends every client one message: those events plus a
 * full snapshot. Sending after the task, not inside the listener callbacks, means the snapshot
 * already shows what the session did next (for example that the engine started thinking after
 * the human's move).
 *
 * <p>Protocol, client to server ({@code type} field): {@code move} {uci}, {@code undo},
 * {@code hint}, {@code newGame} {mode: engine|friend|computer, color: white|black|random,
 * level: 1-10, blackLevel: 1-10 (computer mode: Black's level; level is then White's),
 * champion: {run, generation} (optional: the built-in engine plays with that evolved champion's
 * weights, at level 7 at most), time: {initialMs, incrementMs} (optional; absent or null is
 * untimed)}, {@code resign}, {@code offerDraw}, {@code answerDraw} {accept}, {@code loadPgn}
 * {pgn} (the game becomes a two-player game from its last position). Server to client: {@code {"type":"state","events":[...],"state":{...}}};
 * the state's {@code opponent} is the champion being played ({run, generation, label}), or null. An event is
 * {@code {kind: move|reset|config|hint|gameOver|ended|drawOffer|drawDeclined|rejected, ...}}.
 */
final class GameHub implements GameListener {

    /** A connected browser: where to send its messages. */
    interface Client {
        void send(String json);
    }

    private final ExecutorService gameThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game");
        t.setDaemon(true);
        return t;
    });
    private final Set<Client> clients = new CopyOnWriteArraySet<>();
    private GameSession session;
    /** Events of the task now running on the game thread; only touched there. */
    private final JsonArray pending = new JsonArray();
    /** The last hint, shown until the position changes; only touched on the game thread. */
    private ChessMove hint;
    /** The built-in engine, whose weights a "play the champion" game swaps; null if not given. */
    private MinimaxEngine builtIn;
    private LabApi lab;
    /** The champion the engine plays as ({run, generation, label}), or null; game thread only. */
    private JsonObject opponent;

    /** Lets new games play an evolved champion: {@code builtIn} is the session's built-in engine. */
    void useBuiltIn(MinimaxEngine builtIn) {
        this.builtIn = builtIn;
    }

    void useLab(LabApi lab) {
        this.lab = lab;
    }

    /** The dispatcher to hand the session: runs a task on the game thread, then broadcasts. */
    void execute(Runnable task) {
        gameThread.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            flush();
        });
    }

    void attach(GameSession session) {
        this.session = session;
        session.addListener(this);
    }

    void connect(Client client) {
        execute(() -> clients.add(client)); // the flush after this task sends it the snapshot
    }

    void disconnect(Client client) {
        clients.remove(client);
    }

    /** Parses and runs one client message on the game thread. Malformed input is reported back to that client. */
    void onMessage(Client client, String text) {
        execute(() -> {
            try {
                handle(JsonParser.parseString(text).getAsJsonObject());
            } catch (RuntimeException e) {
                JsonObject rejected = event("rejected");
                rejected.addProperty("reason", String.valueOf(e.getMessage()));
                JsonArray only = new JsonArray();
                only.add(rejected);
                client.send(message(only));
            }
        });
    }

    void shutdown() {
        gameThread.execute(session::shutdown);
        gameThread.shutdown();
    }

    // ---- commands ----------------------------------------------------------

    private void handle(JsonObject msg) {
        String type = msg.get("type").getAsString();
        switch (type) {
            case "move" -> {
                String uci = msg.get("uci").getAsString();
                // a move that comes after the flag fell is answered by the loss on time, not a rejection
                if (session.playHumanMove(ChessMove.fromUci(uci)) == null && session.end() == null) {
                    JsonObject e = event("rejected");
                    e.addProperty("reason", "illegal or not your turn: " + uci);
                    pending.add(e);
                }
            }
            case "undo" -> session.undo();
            case "hint" -> session.requestHint();
            case "newGame" -> newGame(msg);
            case "resign" -> refuseUnless(session.resign(), "nobody can resign now");
            case "offerDraw" -> refuseUnless(session.offerDraw(), "a draw cannot be offered now");
            case "answerDraw" -> refuseUnless(session.answerDraw(msg.get("accept").getAsBoolean()), "no draw offer is waiting");
            case "loadPgn" -> loadPgn(msg.get("pgn").getAsString());
            default -> throw new IllegalArgumentException("unknown message type: " + type);
        }
    }

    private void refuseUnless(boolean done, String reason) {
        if (!done) {
            JsonObject e = event("rejected");
            e.addProperty("reason", reason);
            pending.add(e);
        }
    }

    private void loadPgn(String pgn) {
        try {
            Pgn.read(pgn); // check it before touching the game
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not read the PGN: " + e.getMessage());
        }
        if (builtIn != null) {
            builtIn.useEvaluator(BitBoardEvaluate.DEFAULT);
        }
        opponent = null;
        session.updateConfig(session.config().withMode(GameConfig.Mode.HUMAN_VS_HUMAN));
        session.loadPgn(pgn);
    }

    private static TimeControl timeControl(JsonObject msg) {
        JsonElement time = msg.get("time");
        if (time == null || time.isJsonNull()) {
            return TimeControl.NONE;
        }
        JsonObject t = time.getAsJsonObject();
        return new TimeControl(t.get("initialMs").getAsLong(), t.has("incrementMs") ? t.get("incrementMs").getAsLong() : 0);
    }

    private void newGame(JsonObject msg) {
        GameConfig old = session.config();
        GameConfig.Mode mode = switch (string(msg, "mode", "engine")) {
            case "friend" -> GameConfig.Mode.HUMAN_VS_HUMAN;
            case "computer" -> GameConfig.Mode.ENGINE_VS_ENGINE;
            default -> GameConfig.Mode.HUMAN_VS_ENGINE;
        };
        boolean white = switch (string(msg, "color", old.humanPlaysWhite() ? "white" : "black")) {
            case "black" -> false;
            case "random" -> ThreadLocalRandom.current().nextBoolean();
            default -> true;
        };
        int level = msg.has("level") ? GameStateJson.skillLevel(msg.get("level").getAsInt()) : old.skillLevel();
        // engine vs engine: Black may play at its own level
        int blackLevel = mode == GameConfig.Mode.ENGINE_VS_ENGINE && msg.has("blackLevel")
                ? GameStateJson.skillLevel(msg.get("blackLevel").getAsInt()) : level;
        JsonObject newOpponent = null;
        JsonElement champion = msg.get("champion");
        if (builtIn != null) {
            if (champion != null && !champion.isJsonNull()) {
                if (lab == null) {
                    throw new IllegalStateException("no lab to play a champion from");
                }
                String run = champion.getAsJsonObject().get("run").getAsString();
                int generation = champion.getAsJsonObject().get("generation").getAsInt();
                builtIn.useEvaluator(new BitBoardEvaluate(lab.champion(run, generation)));
                newOpponent = new JsonObject();
                newOpponent.addProperty("run", run);
                newOpponent.addProperty("generation", generation);
                newOpponent.addProperty("label", "Champion of " + lab.name(run) + ", generation " + generation);
                // the champion is the built-in engine; levels from 8 up would hand the game to Stockfish
                level = Math.min(level, EngineSelector.STOCKFISH_FROM_LEVEL - 1);
                blackLevel = Math.min(blackLevel, EngineSelector.STOCKFISH_FROM_LEVEL - 1);
            } else {
                builtIn.useEvaluator(BitBoardEvaluate.DEFAULT);
            }
        }
        opponent = newOpponent;
        GameConfig config = new GameConfig(mode, white, level, blackLevel);
        // reset first, so the engine does not start a move in the old position under the new config
        session.updateConfig(new GameConfig(GameConfig.Mode.HUMAN_VS_HUMAN, white, level));
        session.newGame(rules.Position.START_FEN, timeControl(msg));
        session.updateConfig(config);
    }

    private static String string(JsonObject msg, String key, String fallback) {
        JsonElement e = msg.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsString();
    }

    // ---- session events (on the game thread) -------------------------------

    @Override
    public void moveMade(MoveResult move, boolean byEngine) {
        hint = null;
        JsonObject e = event("move");
        e.add("move", GameStateJson.move(move));
        e.addProperty("byEngine", byEngine);
        pending.add(e);
    }

    @Override
    public void gameOver(MoveResult lastMove) {
        JsonObject e = event("gameOver");
        e.addProperty("status", lastMove.status().name());
        pending.add(e);
    }

    @Override
    public void positionReset() {
        hint = null;
        pending.add(event("reset"));
    }

    @Override
    public void configChanged(GameConfig config) {
        hint = null;
        pending.add(event("config"));
    }

    @Override
    public void gameEnded(GameEnd end) {
        JsonObject e = event("ended");
        e.addProperty("reason", end.reason().name());
        e.addProperty("side", end.white() ? "white" : "black");
        e.addProperty("result", end.result());
        pending.add(e);
    }

    @Override
    public void drawOffered(boolean byWhite) {
        JsonObject e = event("drawOffer");
        e.addProperty("by", byWhite ? "white" : "black");
        pending.add(e);
    }

    @Override
    public void drawDeclined(boolean offeredByWhite) {
        JsonObject e = event("drawDeclined");
        e.addProperty("by", offeredByWhite ? "white" : "black");
        pending.add(e);
    }

    @Override
    public void hint(ChessMove move) {
        hint = move;
        JsonObject e = event("hint");
        e.addProperty("uci", move.toUci());
        pending.add(e);
    }

    // ---- sending -----------------------------------------------------------

    private void flush() {
        JsonArray events = pending.deepCopy();
        while (!pending.isEmpty()) {
            pending.remove(0);
        }
        String json = message(events);
        for (Client c : List.copyOf(clients)) {
            try {
                c.send(json);
            } catch (RuntimeException e) {
                clients.remove(c);
            }
        }
    }

    private String message(JsonArray events) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "state");
        msg.add("events", events);
        JsonObject state = GameStateJson.snapshot(session, hint,
                opponent == null ? null : opponent.get("label").getAsString());
        state.add("opponent", opponent == null ? null : opponent.deepCopy());
        msg.add("state", state);
        return msg.toString();
    }

    private static JsonObject event(String kind) {
        JsonObject e = new JsonObject();
        e.addProperty("kind", kind);
        return e;
    }
}
