package web;

import ai.eval.Evaluators;
import ai.variant.Variant;
import ai.variant.VariantJson;
import ai.variant.Variants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.EngineSelector;
import engine.Levels;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import engine.Weights;
import game.GameArchive;
import game.GameConfig;
import game.GameListener;
import game.GameSession;
import game.GameEnd;
import game.SavedGame;
import game.TimeControl;
import game.VariantStore;
import rules.ChessMove;
import rules.MoveResult;
import rules.Pgn;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
 * {@code hint} {level?: 0-13 (play the hint as that level would; absent: the strongest)}, {@code newGame} {mode: engine|friend|computer, color: white|black|random,
 * level: 0-13, blackLevel: 0-13 (engine.Levels) (computer mode: Black's level; level is then White's),
 * champion: {run, generation} (optional: the built-in engine plays with that evolved champion's
 * weights, at the built-in engine's levels only), weights: tuned|classic (optional: the weights the
 * built-in engine plays with, engine.Weights; absent keeps the last game's), time: {initialMs, incrementMs} (optional; absent or null is
 * untimed), variant: an id from ai.variant.Variants (optional, absent is chess; Stockfish plays chess
 * only, so in another variant its levels play the built-in engine's top level, and a champion
 * needs the chess pieces)}, {@code resign}, {@code offerDraw}, {@code answerDraw} {accept}, {@code loadPgn}
 * {pgn} (the game becomes a two-player game from its last position), {@code resumeGame} {id} (carries on an
 * unfinished saved game, see {@link GamesApi}). Server to client: {@code {"type":"state","events":[...],"state":{...}}};
 * the state's {@code variant} is {id, name, goal, checksToWin?}; {@code opponent} is the champion being played ({run, generation, label}), or null;
 * {@code weights} the built-in engine's weights when it is not a champion ("tuned" or "classic");
 * {@code stockfish} is {available, path} (whether Stockfish's levels really get Stockfish). An event is
 * {@code {kind: move|reset|config|hint|gameOver|ended|drawOffer|drawDeclined|rejected, ...}}.
 *
 * <p>Saved games: every game a person plays (not engine against engine, not a loaded PGN) is saved
 * to the {@link GameArchive} after each task that changed its moves or result, and once more when
 * it is left (a new game, a resumed one, the server stopping), so an unfinished game keeps its
 * clocks and can be carried on. The state's {@code savedId} names the current game's file.
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
    /** The weights the built-in engine plays with when it is not a champion; picked per new game. */
    private Weights weights = Weights.DEFAULT;

    /** Where games are saved, or null to save nothing. */
    private GameArchive archive;
    /** The current game's id in the archive, or null before its first save; game thread only. */
    private String recordId;
    /** When the current game began. */
    private Instant recordStarted = Instant.now();
    /** False for games that are not saved (engine against engine, a loaded PGN). */
    private boolean recording = true;
    /** Moves and result at the last save, so a task that changed neither saves nothing. */
    private String savedSignature;

    /** The Stockfish that plays Levels 9-13, or null if the session was built without one (tests). */
    private StockfishEngine stockfish;

    /** Reports whether Levels 9-13 really get Stockfish ({@code state.stockfish}). */
    void useStockfish(StockfishEngine stockfish) {
        this.stockfish = stockfish;
    }

    /** The session's Stockfish, or null if it was built without one. */
    StockfishEngine stockfish() {
        return stockfish;
    }

    /** Sends every browser a fresh state, e.g. after Stockfish was installed. */
    void refresh() {
        execute(() -> { });
    }

    /** Lets new games play an evolved champion: {@code builtIn} is the session's built-in engine. */
    void useBuiltIn(MinimaxEngine builtIn) {
        this.builtIn = builtIn;
    }

    void useLab(LabApi lab) {
        this.lab = lab;
    }

    void useArchive(GameArchive archive) {
        this.archive = archive;
    }

    /** The player's own variants, playable next to the built-in ones (Phase 6 R5a); null: built-ins only. */
    private VariantStore variants;

    void useVariants(VariantStore variants) {
        this.variants = variants;
    }

    private java.util.Optional<Variant> variantById(String id) {
        return variants != null ? variants.byId(id) : Variants.byId(id);
    }

    private java.util.Optional<Variant> variantByName(String name) {
        return variants != null ? variants.byName(name) : Pgn.builtIn(name);
    }

    /** The dispatcher to hand the session: runs a task on the game thread, then broadcasts. */
    void execute(Runnable task) {
        if (gameThread.isShutdown()) {
            return; // the server is stopping: a late message or engine result has nowhere to go
        }
        gameThread.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            record(false);
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
                try {
                    client.send(message(only));
                } catch (Exception closed) {
                    clients.remove(client);
                }
            }
        });
    }

    void shutdown() {
        gameThread.execute(() -> {
            record(true); // the clocks as they stand, for carrying the game on later
            session.shutdown();
        });
        gameThread.shutdown();
        try {
            gameThread.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
            case "hint" -> session.requestHint(msg.has("level") && !msg.get("level").isJsonNull()
                    ? msg.get("level").getAsInt() : EngineSelector.HINT_LEVEL);
            case "newGame" -> newGame(msg);
            case "resign" -> refuseUnless(session.resign(), "nobody can resign now");
            case "offerDraw" -> refuseUnless(session.offerDraw(), "a draw cannot be offered now");
            case "answerDraw" -> refuseUnless(session.answerDraw(msg.get("accept").getAsBoolean()), "no draw offer is waiting");
            case "loadPgn" -> loadPgn(msg.get("pgn").getAsString());
            case "resumeGame" -> resume(msg.get("id").getAsString());
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
        Pgn.Parsed parsed;
        try {
            parsed = Pgn.read(pgn, this::variantByName); // check it before touching the game
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not read the PGN: " + e.getMessage());
        }
        leaveGame(false);
        if (builtIn != null) {
            builtIn.useEvaluator(weights.evaluator());
        }
        opponent = null;
        session.updateConfig(session.config().withMode(GameConfig.Mode.HUMAN_VS_HUMAN));
        session.loadPgn(parsed);
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
        int level = msg.has("level") ? Levels.clamp(msg.get("level").getAsInt()) : old.skillLevel();
        // engine vs engine: Black may play at its own level
        int blackLevel = mode == GameConfig.Mode.ENGINE_VS_ENGINE && msg.has("blackLevel")
                ? Levels.clamp(msg.get("blackLevel").getAsInt()) : level;
        String variantId = string(msg, "variant", Variants.CHESS.id());
        Variant variant = variantById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("unknown variant: " + variantId));
        JsonElement champion = msg.get("champion");
        boolean playsChampion = champion != null && !champion.isJsonNull();
        leaveGame(mode != GameConfig.Mode.ENGINE_VS_ENGINE);
        if (msg.has("weights") && !msg.get("weights").isJsonNull()) {
            weights = Weights.of(msg.get("weights").getAsString());
        }
        playAs(playsChampion ? champion.getAsJsonObject().get("run").getAsString() : null,
                playsChampion ? champion.getAsJsonObject().get("generation").getAsInt() : 0, variant);
        if (opponent != null || !variant.equals(Variants.CHESS)) {
            // the champion is the built-in engine, and Stockfish plays only chess: its levels would
            // hand the game to Stockfish, or say they did
            level = Math.min(level, EngineSelector.STOCKFISH_FROM_LEVEL - 1);
            blackLevel = Math.min(blackLevel, EngineSelector.STOCKFISH_FROM_LEVEL - 1);
        }
        GameConfig config = new GameConfig(mode, white, level, blackLevel);
        // reset first, so the engine does not start a move in the old position under the new config
        session.updateConfig(new GameConfig(GameConfig.Mode.HUMAN_VS_HUMAN, white, level));
        session.newGame(variant, variant.startFen(), timeControl(msg));
        session.updateConfig(config);
    }

    /**
     * The engine plays as an evolved champion ({@code run} and {@code generation}), or with its usual
     * weights when {@code run} is null. Sets {@link #opponent}.
     */
    private void playAs(String run, int generation, Variant variant) {
        opponent = null;
        if (builtIn == null) {
            return;
        }
        if (run == null) {
            builtIn.useEvaluator(weights.evaluator());
            return;
        }
        if (lab == null) {
            throw new IllegalStateException("no lab to play a champion from");
        }
        LabApi.Champion champion = lab.champion(run, generation);
        if (!Evaluators.schema(variant).equals(Evaluators.schema(champion.variant()))) {
            throw new IllegalArgumentException("that champion plays " + champion.variant().name() + ", not " + variant.name());
        }
        builtIn.useEvaluator(Evaluators.evaluator(variant, champion.params()));
        opponent = new JsonObject();
        opponent.addProperty("run", run);
        opponent.addProperty("generation", generation);
        opponent.addProperty("variant", champion.variant().id());
        opponent.addProperty("label", "Champion of " + lab.name(run) + ", generation " + generation);
    }

    /** Carries on an unfinished saved game where it was left, clocks included. */
    private void resume(String id) {
        if (archive == null) {
            throw new IllegalStateException("games are not being saved");
        }
        SavedGame saved = archive.get(id).orElseThrow(() -> new IllegalArgumentException("no saved game " + id));
        if (saved.finished()) {
            throw new IllegalArgumentException("that game is over");
        }
        // a made variant comes with the game, as it was when the game was played
        Variant variant = saved.variantDef() != null ? VariantJson.read(saved.variantDef())
                : variantById(saved.variant()).orElseThrow(() -> new IllegalArgumentException("unknown variant " + saved.variant()));
        List<ChessMove> moves = saved.moves().stream().map(ChessMove::fromUci).toList();
        rules.Game check = new rules.Game(variant, saved.startFen()); // a damaged file fails here, before anything changes
        moves.forEach(check::play);
        record(true);
        weights = Weights.of(saved.weights());
        playAs(saved.championRun(), saved.championGeneration() == null ? 0 : saved.championGeneration(), variant);
        TimeControl control = saved.timeControl();
        long initial = control.initialMs();
        session.updateConfig(new GameConfig(GameConfig.Mode.HUMAN_VS_HUMAN, saved.config().humanPlaysWhite(), saved.config().skillLevel()));
        session.resume(variant, saved.startFen(), moves, control,
                saved.whiteMs() == null ? initial : saved.whiteMs(), saved.blackMs() == null ? initial : saved.blackMs(),
                saved.started().atZone(ZoneId.systemDefault()).toLocalDate());
        session.updateConfig(saved.config());
        recordId = saved.id();
        recordStarted = saved.started();
        recording = true;
        savedSignature = signature();
    }

    /** Saves the game being left one last time; the next one is saved if {@code recordNext}. */
    private void leaveGame(boolean recordNext) {
        record(true);
        recordId = null;
        recordStarted = Instant.now();
        recording = recordNext;
        savedSignature = null;
    }

    private String signature() {
        return session.moves().size() + " " + session.result();
    }

    /**
     * Saves the current game if its moves or result changed since the last save (or always, when
     * {@code force}). A game taken back to no moves at all is removed again. A failure to save is
     * reported and the game goes on.
     */
    private void record(boolean force) {
        if (archive == null || !recording || session.config().mode() == GameConfig.Mode.ENGINE_VS_ENGINE) {
            return;
        }
        String signature = signature();
        if (!force && signature.equals(savedSignature)) {
            return;
        }
        try {
            List<MoveResult> moves = session.moves();
            if (moves.isEmpty()) {
                if (recordId != null) {
                    archive.delete(recordId);
                }
            } else {
                if (recordId == null) {
                    recordId = archive.newId(recordStarted);
                }
                archive.save(savedGame());
            }
            savedSignature = signature;
        } catch (RuntimeException e) {
            System.err.println("Could not save the game: " + e);
        }
    }

    private SavedGame savedGame() {
        String label = opponent == null ? null : opponent.get("label").getAsString();
        game.Clock clock = session.clock();
        String result = session.result();
        return new SavedGame(recordId, recordStarted, Instant.now(), session.config(),
                opponent == null ? null : opponent.get("run").getAsString(),
                opponent == null ? null : opponent.get("generation").getAsInt(), label,
                session.timeControl(),
                clock == null ? null : clock.remainingMs(true), clock == null ? null : clock.remainingMs(false),
                session.startFen(), session.moves().stream().map(m -> m.move().toUci()).toList(),
                result, result == null ? null : GameStateJson.termination(session),
                GameStateJson.pgn(session, label), weights.id(), session.variant().id(),
                VariantStore.isBuiltIn(session.variant().id()) ? null : VariantJson.toTree(session.variant()).toString());
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
            } catch (Exception e) {
                // the browser went away (a closed socket throws ClosedChannelException, unchecked
                // through Javalin); drop it quietly, its onClose may not have arrived yet
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
        state.addProperty("savedId", recording ? recordId : null);
        state.addProperty("weights", weights.id());
        if (stockfish != null) {
            JsonObject sf = new JsonObject();
            sf.addProperty("available", stockfish.isAvailable());
            sf.addProperty("path", stockfish.path());
            state.add("stockfish", sf);
        }
        msg.add("state", state);
        return msg.toString();
    }

    private static JsonObject event(String kind) {
        JsonObject e = new JsonObject();
        e.addProperty("kind", kind);
        return e;
    }
}
