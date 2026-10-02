package game;

import engine.Cancellation;
import engine.EngineSelector;
import engine.SearchRequest;
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
 * <p>Threading — the one concurrency pattern for engine work (Phase 4, docs/phase-4-research.md
 * Fork B1):
 * <ul>
 *   <li>Every method must be called on the <em>dispatcher</em> thread (the Swing event thread in
 *       the desktop app), and every {@link GameListener} callback is delivered there. Game state
 *       is only ever touched on that thread.</li>
 *   <li>Engine moves and hints run as jobs on one engine thread. Each job carries a
 *       {@link Cancellation}; the engines stop thinking soon after it is set, so a cancelled job
 *       frees the engine thread almost at once.</li>
 *   <li>A take-back, new game or config change cancels every job. Any move, and starting the
 *       engine's move, cancels a pending hint (it was for an older position, or would delay the
 *       engine). A new hint request replaces a pending one for another position, so at most one
 *       hint is ever waiting.</li>
 *   <li>A job's result is handed back to the dispatcher and dropped if the game moved on in the
 *       meantime (generation check), so a late answer can never be played.</li>
 *   <li>A job that fails, with an exception or an {@code Error}, is reported on stderr. A failed
 *       engine move is replaced by a quick built-in move so the game goes on (Phase 4b); a failed
 *       hint simply doesn't arrive and can be asked for again.</li>
 * </ul>
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
    private Cancellation engineCancel = new Cancellation();
    private Future<?> pendingHintJob;
    private Cancellation hintCancel = new Cancellation();
    /** The position the pending hint is for, or {@code null} when no hint is pending. */
    private String hintFen;
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

    /**
     * Asks the engine for a suggestion for the side to move; arrives as {@link GameListener#hint}.
     * Asking again for the same position while a hint is on its way does nothing.
     */
    public void requestHint() {
        if (isOver()) {
            return;
        }
        String fen = game.fen();
        if (fen.equals(hintFen)) {
            return;
        }
        cancelHint();
        long gen = generation;
        Cancellation cancel = new Cancellation();
        hintCancel = cancel;
        hintFen = fen;
        SearchRequest request = request(EngineSelector.HINT_LEVEL, cancel);
        pendingHintJob = engineExecutor.submit(() -> {
            ChessMove move = cancel.isCancelled() ? null : hintOrNull(request);
            dispatcher.execute(() -> {
                if (cancel == hintCancel) {
                    hintFen = null;
                }
                if (move != null && !cancel.isCancelled() && gen == generation && fen.equals(game.fen())) {
                    listeners.forEach(l -> l.hint(move));
                }
            });
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
        cancelHint(); // it was for the position before this move
        listeners.forEach(l -> l.moveMade(result, byEngine));
        if (result.status().isGameOver()) {
            listeners.forEach(l -> l.gameOver(result));
        }
        return result;
    }

    /** Cancels the engine's move and any hint; their results, if they still arrive, are dropped. */
    private void cancelEngine() {
        generation++;
        engineBusy = false;
        engineCancel.cancel();
        if (pendingEngineJob != null) {
            pendingEngineJob.cancel(true);
            pendingEngineJob = null;
        }
        cancelHint();
    }

    private void cancelHint() {
        hintCancel.cancel();
        hintFen = null;
        if (pendingHintJob != null) {
            pendingHintJob.cancel(true);
            pendingHintJob = null;
        }
    }

    private SearchRequest request(int level, Cancellation cancel) {
        List<ChessMove> played = game.moves().stream().map(MoveResult::move).toList();
        return new SearchRequest(game.fen(), game.history().get(0), played, level, cancel);
    }

    private void maybeStartEngine() {
        if (isOver() || config.isHuman(whiteToMove()) || engineBusy) {
            return;
        }
        cancelHint(); // the engine's own move goes first
        engineBusy = true;
        String fen = game.fen();
        int level = config.skillLevel();
        long gen = generation;
        long delay = level == 0 ? randomMoveDelayMs : 0;
        Cancellation cancel = new Cancellation();
        engineCancel = cancel;
        SearchRequest request = request(level, cancel);
        pendingEngineJob = engineExecutor.submit(() -> {
            try {
                if (delay > 0) {
                    Thread.sleep(delay);
                }
                ChessMove move = engines.move(request);
                dispatcher.execute(() -> engineMoveReady(gen, fen, move));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | Error e) {
                // never swallow: a silent failure here is how "the engine stops playing" hid. An
                // Error (say, out of memory in a deep search) used to end the job without a word.
                e.printStackTrace();
                ChessMove move = quickMoveAfterFailure(request);
                dispatcher.execute(() -> engineMoveReady(gen, fen, move));
            }
            return null;
        });
    }

    /** After a failed search, a quick built-in move keeps the game going; null if that fails too. */
    private ChessMove quickMoveAfterFailure(SearchRequest request) {
        if (request.cancel().isCancelled()) {
            return null;
        }
        try {
            return engines.quickMove(request);
        } catch (RuntimeException | Error e) {
            e.printStackTrace();
            return null;
        }
    }

    /** The hint, or null if its search failed; the failure is reported and the hint can be asked for again. */
    private ChessMove hintOrNull(SearchRequest request) {
        try {
            return engines.hint(request);
        } catch (RuntimeException | Error e) {
            e.printStackTrace();
            return null;
        }
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
