package game;

import engine.Cancellation;
import engine.DrawOffers;
import engine.EngineSelector;
import engine.SearchRequest;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;
import rules.Pgn;
import rules.Position;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * One game being played: the {@link Game} (position + history), the {@link GameConfig}, and the
 * turn-taking between humans and the engine. UI-free: the web server ({@code web.GameHub}) is its
 * client (Phase 3, docs/phase-3-research.md Fork 1; Phase 4c).
 *
 * <p>Threading — the one concurrency pattern for engine work (Phase 4, docs/phase-4-research.md
 * Fork B1):
 * <ul>
 *   <li>Every method must be called on the <em>dispatcher</em> thread ({@code web.GameHub}'s
 *       game thread), and every {@link GameListener} callback is delivered there. Game state
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
 *   <li>The clock's flag is watched by a timer thread that only wakes the dispatcher, which
 *       checks the clock itself; a move that arrives after the flag fell loses on time.</li>
 * </ul>
 *
 * <p>A game ends on the board ({@link #status()}: mate and the automatic draws, threefold and
 * fifty moves included) or off it ({@link #end()}: resignation, time, a draw agreed).
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

    private TimeControl timeControl = TimeControl.NONE;
    /** The game's clock, or {@code null} in an untimed game. */
    private Clock clock;
    private LongSupplier nanoTime = System::nanoTime;
    private ScheduledExecutorService clockTimer;
    private ScheduledFuture<?> flagWatch;
    /** How the game ended off the board, or {@code null}. */
    private GameEnd end;
    /** Between two humans: the side whose draw offer waits for an answer, or {@code null}. */
    private Boolean drawOfferBy;
    /** Against the engine: the ply at which it last declined a draw (no new offer until the human moves). */
    private int drawDeclinedAtPly = -1;
    private LocalDate date = LocalDate.now();

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

    /** For tests: the clock's time source. Takes effect from the next new game. */
    void setNanoTime(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
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
        return end != null || game.status().isGameOver();
    }

    /** How the game ended off the board (resignation, time, agreement), or {@code null}. */
    public GameEnd end() {
        return end;
    }

    /** {@code "1-0"}, {@code "0-1"}, {@code "1/2-1/2"}, or {@code null} while the game is on. */
    public String result() {
        if (end != null) {
            return end.result();
        }
        GameStatus status = game.status();
        if (status == GameStatus.CHECKMATE) {
            return whiteToMove() ? "0-1" : "1-0";
        }
        return status.isDraw() ? "1/2-1/2" : null;
    }

    public TimeControl timeControl() {
        return timeControl;
    }

    /** The clock, or {@code null} in an untimed game. Read it on the dispatcher thread only. */
    public Clock clock() {
        return clock;
    }

    /** The day the game started (for its PGN). */
    public LocalDate date() {
        return date;
    }

    public String startFen() {
        return game.history().get(0);
    }

    /** Between two humans: the side whose draw offer is waiting ({@code TRUE} White), or {@code null}. */
    public Boolean drawOfferBy() {
        return drawOfferBy;
    }

    /** True if a draw can be offered now: a game with a human in it, not over, no offer waiting. */
    public boolean canOfferDraw() {
        return switch (config.mode()) {
            case ENGINE_VS_ENGINE -> false;
            case HUMAN_VS_HUMAN -> !isOver() && drawOfferBy == null;
            case HUMAN_VS_ENGINE -> !isOver() && game.plyCount() != drawDeclinedAtPly;
        };
    }

    /** True if someone can resign now: a game with a human in it that is not over. */
    public boolean canResign() {
        return !isOver() && config.mode() != GameConfig.Mode.ENGINE_VS_ENGINE;
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

    /** True while the engine is computing its move (from the request until it is played or dropped). */
    public boolean isEngineThinking() {
        return engineBusy;
    }

    /** True while a hint has been asked for and has not arrived (or been dropped) yet. */
    public boolean isHintPending() {
        return hintFen != null;
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
        if (flagFell() || !isHumanTurn() || !isLegal(move)) {
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
        newGame(fen, timeControl);
    }

    /** A new game from {@code fen} with this time control ({@link TimeControl#NONE}: untimed). */
    public void newGame(String fen, TimeControl control) {
        cancelEngine();
        game = new Game(fen);
        resetEnding(control);
        listeners.forEach(GameListener::positionReset);
        maybeStartEngine();
    }

    /**
     * Loads a game written in PGN, untimed, to look through or play on from its last position.
     *
     * @throws IllegalArgumentException if it is not a legal game (nothing changes then)
     */
    public void loadPgn(String pgn) {
        Pgn.Parsed parsed = Pgn.read(pgn);
        Game loaded = new Game(parsed.startFen());
        parsed.moves().forEach(loaded::play);
        cancelEngine();
        game = loaded;
        resetEnding(TimeControl.NONE);
        listeners.forEach(GameListener::positionReset);
        maybeStartEngine();
    }

    /**
     * The human resigns: against the engine, the human's side; between two humans, the side to
     * move. Returns false if nobody can resign now (see {@link #canResign()}).
     */
    public boolean resign() {
        if (!canResign()) {
            return false;
        }
        boolean white = config.mode() == GameConfig.Mode.HUMAN_VS_ENGINE ? config.humanPlaysWhite() : whiteToMove();
        finish(GameEnd.resigned(white));
        return true;
    }

    /**
     * A draw offer. Against the engine it is answered at once: accepted when the position has
     * been seen before or the engine is not better ({@link DrawOffers}); once declined, the human
     * moves before offering again. Between two humans, the side to move offers and the other side
     * answers with {@link #answerDraw}; a move by the other side declines it. Returns false if no
     * offer can be made now.
     */
    public boolean offerDraw() {
        if (!canOfferDraw()) {
            return false;
        }
        if (config.mode() == GameConfig.Mode.HUMAN_VS_HUMAN) {
            drawOfferBy = whiteToMove();
            boolean by = drawOfferBy;
            listeners.forEach(l -> l.drawOffered(by));
            return true;
        }
        boolean human = config.humanPlaysWhite();
        if (currentPositionRepeated() || DrawOffers.engineAccepts(game.fen(), !human)) {
            finish(GameEnd.agreed());
        } else {
            drawDeclinedAtPly = game.plyCount();
            listeners.forEach(l -> l.drawDeclined(human));
        }
        return true;
    }

    /** Between two humans: accepts or declines the waiting draw offer. False if none is waiting. */
    public boolean answerDraw(boolean accept) {
        if (drawOfferBy == null || isOver()) {
            return false;
        }
        boolean by = drawOfferBy;
        drawOfferBy = null;
        if (accept) {
            finish(GameEnd.agreed());
        } else {
            listeners.forEach(l -> l.drawDeclined(by));
        }
        return true;
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
        if (game.plyCount() == 0 || end != null || config.mode() == GameConfig.Mode.ENGINE_VS_ENGINE) {
            return false;
        }
        cancelEngine();
        game.undo();
        while (game.plyCount() > 0 && !config.isHuman(whiteToMove())) {
            game.undo();
        }
        drawOfferBy = null;
        drawDeclinedAtPly = -1;
        if (clock != null) {
            // the clocks keep the time they have; the side to move runs again (none before move one)
            if (game.plyCount() == 0) {
                clock.stop();
            } else {
                clock.startFor(whiteToMove());
            }
            watchFlag();
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
        if (clockTimer != null) {
            clockTimer.shutdownNow();
        }
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
        if (drawOfferBy != null && drawOfferBy != result.whiteMoved()) {
            drawOfferBy = null; // answered with a move: declined
        }
        if (clock != null) {
            if (result.status().isGameOver()) {
                clock.stop();
            } else {
                clock.moveMade(result.whiteMoved());
            }
            watchFlag();
        }
        listeners.forEach(l -> l.moveMade(result, byEngine));
        if (result.status().isGameOver()) {
            listeners.forEach(l -> l.gameOver(result));
        }
        return result;
    }

    private void resetEnding(TimeControl control) {
        timeControl = control;
        clock = control.isTimed() ? new Clock(control, () -> nanoTime.getAsLong()) : null;
        end = null;
        drawOfferBy = null;
        drawDeclinedAtPly = -1;
        date = LocalDate.now();
        watchFlag();
    }

    /** Ends the game off the board. */
    private void finish(GameEnd how) {
        cancelEngine();
        end = how;
        drawOfferBy = null;
        if (clock != null) {
            clock.stop();
        }
        watchFlag();
        listeners.forEach(l -> l.gameEnded(how));
    }

    private boolean currentPositionRepeated() {
        String key = game.position().repetitionKey();
        return game.history().stream().filter(f -> Position.fromFen(f).repetitionKey().equals(key)).count() >= 2;
    }

    /** If the running side's time is up, ends the game on time and returns true. */
    private boolean flagFell() {
        if (end != null || clock == null || !clock.flagged()) {
            return false;
        }
        boolean white = clock.running();
        finish(GameEnd.flagged(white, canMate(game.position(), !white)));
        return true;
    }

    /** Whether this side has the material to mate at all (a lone king or king and one minor piece cannot). */
    static boolean canMate(Position position, boolean white) {
        int minors = 0;
        for (char piece : position.pieces()) {
            if (Character.isUpperCase(piece) != white) {
                continue;
            }
            switch (Character.toLowerCase(piece)) {
                case 'p', 'r', 'q' -> {
                    return true;
                }
                case 'n', 'b' -> minors++;
                default -> { }
            }
        }
        return minors >= 2;
    }

    /** (Re)arms the timer that wakes the dispatcher when the running side's time should run out. */
    private void watchFlag() {
        if (flagWatch != null) {
            flagWatch.cancel(false);
            flagWatch = null;
        }
        if (clock == null || clock.running() == null || end != null) {
            return;
        }
        if (clockTimer == null) {
            clockTimer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "clock");
                t.setDaemon(true);
                return t;
            });
        }
        Clock watched = clock;
        flagWatch = clockTimer.schedule(() -> dispatcher.execute(() -> {
            if (watched == clock && !flagFell()) {
                watchFlag(); // woke a little early: wait for the rest
            }
        }), clock.msUntilFlag() + 1, TimeUnit.MILLISECONDS);
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
        int level = config.skillLevelFor(whiteToMove());
        long gen = generation;
        long delay = level == 0 ? randomMoveDelayMs : 0;
        Cancellation cancel = new Cancellation();
        engineCancel = cancel;
        SearchRequest request = request(level, cancel);
        pendingEngineJob = engineExecutor.submit(() -> {
            ChessMove move;
            try {
                if (delay > 0) {
                    Thread.sleep(delay);
                }
                move = engines.move(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (RuntimeException | Error e) {
                // never swallow: a silent failure here is how "the engine stops playing" hid. An
                // Error (say, out of memory in a deep search) used to end the job without a word.
                e.printStackTrace();
                move = quickMoveAfterFailure(request);
            }
            ChessMove result = move;
            dispatcher.execute(() -> engineMoveReady(gen, fen, result));
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
        if (flagFell()) {
            return; // the engine thought past its time
        }
        if (move == null) {
            System.err.println("Engine found no move in " + fen);
            return;
        }
        apply(move, true);
        maybeStartEngine();
    }
}
