package game;

import engine.EngineSelector;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;
import rules.Position;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * One game being played: the {@link Game} (position + history), the {@link GameConfig}, and the
 * turn-taking between humans and the engine. Swing-free — the desktop UI is one client; a server
 * could be another (Phase 3, docs/phase-3-research.md Fork 1).
 *
 * <p>Threading: every method must be called on the <em>dispatcher</em> thread, and every
 * {@link GameListener} callback is delivered there. Engine searches run on a separate engine
 * executor; their results are handed back to the dispatcher, and dropped if the game moved on in
 * the meantime (take-back, new game). The single concurrency model is Phase 4.
 */
public final class GameSession {

    /** Pause before a level-0 (random) engine move, so it doesn't appear instantly. */
    static final long RANDOM_MOVE_DELAY_MS = 1000;

    private final EngineSelector engines;
    private final Executor dispatcher;
    private final ExecutorService engineExecutor;
    private final List<GameListener> listeners = new CopyOnWriteArrayList<>();

    private Game game;
    private GameConfig config;
    /** Bumped whenever the position changes other than by the move an engine job is computing. */
    private long generation;
    private Future<?> pendingEngineJob;
    /** True from submitting an engine move until its result is applied or discarded. */
    private boolean engineBusy;
    private long randomMoveDelayMs = RANDOM_MOVE_DELAY_MS;

    public GameSession(GameConfig config, EngineSelector engines, Executor dispatcher) {
        this(config, engines, dispatcher, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "engine");
            t.setDaemon(true);
            return t;
        }));
    }

    public GameSession(GameConfig config, EngineSelector engines, Executor dispatcher,
                       ExecutorService engineExecutor) {
        this.config = config;
        this.engines = engines;
        this.dispatcher = dispatcher;
        this.engineExecutor = engineExecutor;
        this.game = new Game();
    }

    /** For tests: skip the cosmetic pause before random moves. */
    void setRandomMoveDelayMs(long ms) {
        this.randomMoveDelayMs = ms;
    }

    public void addListener(GameListener listener) {
        listeners.add(listener);
    }

    // ---- queries -----------------------------------------------------------

    public GameConfig config() {
        return config;
    }

    public String fen() {
        return game.fen();
    }

    public Position position() {
        return game.position();
    }

    public GameStatus status() {
        return game.status();
    }

    public boolean isOver() {
        return game.status().isGameOver();
    }

    public List<ChessMove> legalMoves() {
        return isOver() ? List.of() : game.legalMoves();
    }

    public List<MoveResult> moves() {
        return game.moves();
    }

    public boolean whiteToMove() {
        return game.position().whiteToMove();
    }

    /** True when the side to move is played by a human and the game is not over. */
    public boolean isHumanTurn() {
        return !isOver() && config.isHuman(whiteToMove());
    }

    // ---- commands ----------------------------------------------------------

    /** Starts the engine if it is the engine's turn (call once after wiring the listeners). */
    public void start() {
        maybeStartEngine();
    }

    /**
     * A human's move. Returns {@code null} (and changes nothing) if it is not a human's turn or the
     * move is illegal. A promotion without a piece promotes to a queen.
     */
    public MoveResult playHumanMove(ChessMove move) {
        if (!isHumanTurn() || !isLegal(move)) {
            return null;
        }
        MoveResult result = apply(move, false);
        maybeStartEngine();
        return result;
    }

    public void newGame() {
        newGame(Position.START_FEN);
    }

    public void newGame(String fen) {
        cancelEngine();
        game = new Game(fen);
        listeners.forEach(GameListener::positionReset);
        maybeStartEngine();
    }

    public void updateConfig(GameConfig newConfig) {
        cancelEngine();
        config = newConfig;
        listeners.forEach(l -> l.configChanged(newConfig));
        maybeStartEngine();
    }

    /**
     * Take back moves until it is a human's turn again (in human-vs-engine that is the engine's
     * reply plus the human's move). Returns false if there was nothing to take back.
     */
    public boolean undo() {
        if (game.plyCount() == 0 || config.mode() == GameConfig.Mode.ENGINE_VS_ENGINE) {
            return false;
        }
        cancelEngine();
        game.undo();
        while (game.plyCount() > 0 && !config.isHuman(whiteToMove())) {
            game.undo();
        }
        listeners.forEach(GameListener::positionReset);
        maybeStartEngine();
        return true;
    }

    /** Asks the engine for a suggestion for the side to move; arrives as {@link GameListener#hint}. */
    public void requestHint() {
        if (isOver()) {
            return;
        }
        String fen = game.fen();
        long gen = generation;
        engineExecutor.submit(() -> {
            ChessMove move = engines.hint(fen);
            if (move != null) {
                dispatcher.execute(() -> {
                    if (gen == generation && fen.equals(game.fen())) {
                        listeners.forEach(l -> l.hint(move));
                    }
                });
            }
            return null;
        });
    }

    /** Stops the engine thread and Stockfish. The session is unusable afterwards. */
    public void shutdown() {
        cancelEngine();
        engineExecutor.shutdownNow();
        engines.close();
    }

    // ---- internals ---------------------------------------------------------

    private boolean isLegal(ChessMove move) {
        for (ChessMove m : game.legalMoves()) {
            if (m.from() == move.from() && m.to() == move.to()
                    && (move.promotion() == 0 || m.promotion() == move.promotion())) {
                return true;
            }
        }
        return false;
    }

    private MoveResult apply(ChessMove move, boolean byEngine) {
        MoveResult result = game.play(move);
        generation++;
        listeners.forEach(l -> l.moveMade(result, byEngine));
        if (result.status().isGameOver()) {
            listeners.forEach(l -> l.gameOver(result));
        }
        return result;
    }

    private void cancelEngine() {
        generation++;
        engineBusy = false;
        if (pendingEngineJob != null) {
            pendingEngineJob.cancel(true);
            pendingEngineJob = null;
        }
    }

    private void maybeStartEngine() {
        if (isOver() || config.isHuman(whiteToMove()) || engineBusy) {
            return;
        }
        engineBusy = true;
        String fen = game.fen();
        int level = config.skillLevel();
        long gen = generation;
        long delay = level == 0 ? randomMoveDelayMs : 0;
        pendingEngineJob = engineExecutor.submit(() -> {
            try {
                if (delay > 0) {
                    Thread.sleep(delay);
                }
                ChessMove move = engines.move(fen, level);
                dispatcher.execute(() -> engineMoveReady(gen, fen, move));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // never swallow: a silent failure here is how "the engine stops playing" hid
                e.printStackTrace();
                dispatcher.execute(() -> engineMoveReady(gen, fen, null));
            }
            return null;
        });
    }

    private void engineMoveReady(long gen, String fen, ChessMove move) {
        if (gen != generation || !fen.equals(game.fen())) {
            return; // stale: the game moved on while the engine was thinking
        }
        engineBusy = false;
        if (move == null) {
            System.err.println("Engine found no move in " + fen);
            return;
        }
        apply(move, true);
        maybeStartEngine();
    }
}
