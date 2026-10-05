package engine;

import ai.eval.ChessEvaluate;
import ai.Minimax;
import ai.board.Boards;
import ai.board.Move;
import ai.eval.Evaluator;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The built-in engine: a random legal move at level 0, otherwise the bitboard minimax search at a
 * depth derived from the level and the game stage ({@link Levels}). Levels above the built-in
 * ladder play its top level.
 *
 * <p>Phase 4 (docs/phase-4-research.md Fork A1): the depth is still the strength knob, but the
 * search deepens one ply at a time and stops at {@link #TIME_CAP_MS}, playing the move of the
 * deepest finished depth. Levels that finish in time play exactly as before. In a game with a
 * clock the cap is also held to the request's {@link SearchRequest#timeBudgetMs()}.
 *
 * <p>Phase 5: it plays with any {@link Evaluator}; the default is the hand-written evaluation
 * with its usual weights. It no longer plays the same game every time: among the moves scoring
 * within {@link #DEFAULT_VARIETY} of the best it picks one at random ({@code searchAtDepth} stays
 * deterministic, for the tests that record moves).
 *
 * <p>It reads the game behind the position ({@link SearchRequest#moves()}), so a move back into a
 * position the game already had is scored as a draw: when it is ahead it does not repeat.
 */
public final class MinimaxEngine implements Engine {

    /** Longest a built-in search may think, at any level. */
    public static final long TIME_CAP_MS = 5000;

    /** How far below the best move (pawn = 100) a move may score and still be played: 0.2 pawn. */
    public static final int DEFAULT_VARIETY = 20;

    private final Random random;
    private final long timeCapMs;
    /** Swapped by {@link #useEvaluator}, e.g. to play an evolved champion; read once per search. */
    private volatile Evaluator evaluator;
    private final int variety;

    public MinimaxEngine() {
        this(new Random());
    }

    public MinimaxEngine(Random random) {
        this(random, TIME_CAP_MS);
    }

    /** For tests: a different time cap. */
    public MinimaxEngine(Random random, long timeCapMs) {
        this(random, timeCapMs, Weights.DEFAULT.evaluator());
    }

    /** The engine playing with {@code evaluator}'s weights. */
    public MinimaxEngine(Evaluator evaluator) {
        this(new Random(), TIME_CAP_MS, evaluator);
    }

    public MinimaxEngine(Random random, long timeCapMs, Evaluator evaluator) {
        this(random, timeCapMs, evaluator, DEFAULT_VARIETY);
    }

    /** {@code variety} 0 always plays the search's best move. */
    public MinimaxEngine(Random random, long timeCapMs, Evaluator evaluator, int variety) {
        this.random = random;
        this.timeCapMs = timeCapMs;
        this.evaluator = evaluator;
        this.variety = variety;
    }

    public Evaluator evaluator() {
        return evaluator;
    }

    /** Plays the following searches with {@code evaluator}'s weights (a search already running keeps its own). */
    public void useEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public ChessMove bestMove(SearchRequest request) {
        String fen = request.fen();
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty() || request.cancel().isCancelled()) {
            return null;
        }
        int level = Math.min(Levels.TOP_BUILT_IN, request.skillLevel());
        if (level <= Levels.RANDOM || random.nextInt(100) < Levels.randomPercent(level)) {
            return legal.get(random.nextInt(legal.size()));
        }
        int depth = searchDepth(Position.fromFen(fen), level);
        long deadline = System.nanoTime() + TimeBudget.cap(timeCapMs, request.timeBudgetMs()) * 1_000_000;
        Evaluator weights = evaluator;
        long[] history = gameHistory(gameFens(request.startFen(), request.moves()), fen);
        int move = Minimax.getBestMove(Boards.fromFen(fen), depth, weights,
                new Minimax.Options(variety, random, true), history,
                () -> request.cancel().isCancelled() || System.nanoTime() > deadline);
        return request.cancel().isCancelled() ? null : toLegalMove(move, legal);
    }

    /** The minimax search's move at exactly {@code depth} (no time cap), or {@code null} if there is no legal move. */
    public static ChessMove searchAtDepth(String fen, int depth) {
        return searchAtDepth(fen, depth, ChessEvaluate.CLASSIC);
    }

    /** The minimax search's move at exactly {@code depth} with {@code evaluator}, or {@code null} if there is no legal move. */
    public static ChessMove searchAtDepth(String fen, int depth, Evaluator evaluator) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        int move = Minimax.getBestMove(Boards.fromFen(fen), depth, evaluator, () -> false);
        return toLegalMove(move, legal);
    }

    /**
     * The minimax search's move at exactly {@code depth} with {@code evaluator} and {@code options}
     * (variety, quiescence), or {@code null} if there is no legal move. The arena plays with this.
     */
    public static ChessMove searchAtDepth(String fen, int depth, Evaluator evaluator, Minimax.Options options) {
        return searchAtDepth(fen, List.of(), depth, evaluator, options);
    }

    /**
     * Like {@link #searchAtDepth(String, int, Evaluator, Minimax.Options)} in a game whose positions so
     * far are {@code gameFens} (oldest first; the current one may be last): moving back into one of
     * them is scored as a draw.
     */
    public static ChessMove searchAtDepth(String fen, List<String> gameFens, int depth, Evaluator evaluator,
                                          Minimax.Options options) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        int move = Minimax.getBestMove(Boards.fromFen(fen), depth, evaluator, options,
                gameHistory(gameFens, fen), () -> false);
        return toLegalMove(move, legal);
    }

    /**
     * Like {@link #searchAtDepth(String, int, Evaluator, Minimax.Options)}, but deepening only while
     * {@code millis} have not passed: the move of the deepest depth finished in time.
     */
    public static ChessMove searchWithin(String fen, int maxDepth, Evaluator evaluator, Minimax.Options options,
                                         long millis) {
        return searchWithin(fen, List.of(), maxDepth, evaluator, options, millis);
    }

    /** {@link #searchWithin(String, int, Evaluator, Minimax.Options, long)} knowing the game's positions so far. */
    public static ChessMove searchWithin(String fen, List<String> gameFens, int maxDepth, Evaluator evaluator,
                                         Minimax.Options options, long millis) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        long deadline = System.nanoTime() + millis * 1_000_000;
        int move = Minimax.getBestMove(Boards.fromFen(fen), maxDepth, evaluator, options,
                gameHistory(gameFens, fen), () -> System.nanoTime() > deadline);
        return toLegalMove(move, legal);
    }

    /** The positions of the game {@code startFen} + {@code moves}, oldest first (the last is the current one). */
    static List<String> gameFens(String startFen, List<ChessMove> moves) {
        List<String> fens = new ArrayList<>(moves.size() + 1);
        String fen = startFen;
        fens.add(fen);
        for (ChessMove move : moves) {
            fen = Rules.applyMove(fen, move);
            fens.add(fen);
        }
        return fens;
    }

    /**
     * The search's repetition keys of the game positions before {@code fen} that it could still
     * repeat: those since the last capture or pawn move (as many as {@code fen}'s half-move clock).
     * {@code gameFens} may end with {@code fen} itself; it is left out (the search adds the root).
     */
    static long[] gameHistory(List<String> gameFens, String fen) {
        int end = gameFens.size();
        if (end > 0 && samePosition(gameFens.get(end - 1), fen)) {
            end--;
        }
        String[] fields = fen.split(" ");
        int halfMoves = fields.length > 4 ? Integer.parseInt(fields[4]) : end;
        int start = Math.max(0, end - halfMoves);
        long[] keys = new long[end - start];
        for (int i = start; i < end; i++) {
            keys[i - start] = Boards.fromFen(gameFens.get(i)).repetitionKey();
        }
        return keys;
    }

    /** Same placement, side to move, castling and en passant (move counters aside). */
    private static boolean samePosition(String a, String b) {
        String[] x = a.split(" ");
        String[] y = b.split(" ");
        return Arrays.equals(x, 0, Math.min(4, x.length), y, 0, Math.min(4, y.length));
    }

    /** The level's depth ({@link Levels#builtInDepth}), plus 2 with at most 8 pieces left, plus 1 with at most 12. */
    static int searchDepth(Position position, int level) {
        int depth = Levels.builtInDepth(level);
        int pieces = position.pieces().size();
        if (pieces <= 8) {
            return depth + 2;
        }
        if (pieces <= 12) {
            return depth + 1;
        }
        return depth;
    }

    /** Maps the search's {@link Move} onto the matching entry of the legal-move list. */
    private static ChessMove toLegalMove(int move, List<ChessMove> legal) {
        if (move == Move.NONE) {
            return null;
        }
        ChessMove match = null;
        for (ChessMove m : legal) {
            if (m.from() != Move.from(move) || m.to() != Move.to(move)) {
                continue;
            }
            if (!m.isPromotion() || m.promotion() == Move.promotion(move)) {
                return m;
            }
            match = m; // a promotion to another piece than the search picked; keep as fallback
        }
        return match;
    }
}
