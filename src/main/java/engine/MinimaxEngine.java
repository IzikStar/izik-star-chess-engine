package engine;

import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitBoardRules;
import ai.BitBoard.BitMove;
import ai.Minimax;
import ai.eval.Evaluator;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.util.List;
import java.util.Random;

/**
 * The built-in engine: a random legal move at level 0, otherwise the bitboard minimax search at a
 * depth derived from the level and the game stage (the formula {@code ai.myEngine} used).
 *
 * <p>Phase 4 (docs/phase-4-research.md Fork A1): the depth is still the strength knob, but the
 * search deepens one ply at a time and stops at {@link #TIME_CAP_MS}, playing the move of the
 * deepest finished depth. Levels that finish in time play exactly as before.
 *
 * <p>Phase 5: it plays with any {@link Evaluator}; the default is the hand-written evaluation
 * with its usual weights. It no longer plays the same game every time: among the moves scoring
 * within {@link #DEFAULT_VARIETY} of the best it picks one at random ({@code searchAtDepth} stays
 * deterministic, for the tests that record moves).
 */
public final class MinimaxEngine implements Engine {

    /** Longest a built-in search may think, at any level. */
    public static final long TIME_CAP_MS = 5000;

    /** How far below the best move (pawn = 10) a move may score and still be played: 0.2 pawn. */
    public static final int DEFAULT_VARIETY = 2;

    private final Random random;
    private final long timeCapMs;
    private final Evaluator evaluator;
    private final int variety;

    public MinimaxEngine() {
        this(new Random());
    }

    public MinimaxEngine(Random random) {
        this(random, TIME_CAP_MS);
    }

    /** For tests: a different time cap. */
    public MinimaxEngine(Random random, long timeCapMs) {
        this(random, timeCapMs, BitBoardEvaluate.DEFAULT);
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

    @Override
    public ChessMove bestMove(SearchRequest request) {
        String fen = request.fen();
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty() || request.cancel().isCancelled()) {
            return null;
        }
        if (request.skillLevel() <= 0) {
            return legal.get(random.nextInt(legal.size()));
        }
        int depth = Math.max(1, searchDepth(Position.fromFen(fen), request.skillLevel()));
        long deadline = System.nanoTime() + timeCapMs * 1_000_000;
        BitMove bitMove = Minimax.getBestMove(BitBoardRules.fromFen(fen), depth, evaluator,
                new Minimax.Options(variety, random, true), () -> request.cancel().isCancelled() || System.nanoTime() > deadline);
        return request.cancel().isCancelled() ? null : toLegalMove(bitMove, legal);
    }

    /** The minimax search's move at exactly {@code depth} (no time cap), or {@code null} if there is no legal move. */
    public static ChessMove searchAtDepth(String fen, int depth) {
        return searchAtDepth(fen, depth, BitBoardEvaluate.DEFAULT);
    }

    /** The minimax search's move at exactly {@code depth} with {@code evaluator}, or {@code null} if there is no legal move. */
    public static ChessMove searchAtDepth(String fen, int depth, Evaluator evaluator) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        BitMove bitMove = Minimax.getBestMove(BitBoardRules.fromFen(fen), depth, evaluator, () -> false);
        return toLegalMove(bitMove, legal);
    }

    /**
     * The minimax search's move at exactly {@code depth} with {@code evaluator} and {@code options}
     * (variety, quiescence), or {@code null} if there is no legal move. The arena plays with this.
     */
    public static ChessMove searchAtDepth(String fen, int depth, Evaluator evaluator, Minimax.Options options) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        BitMove bitMove = Minimax.getBestMove(BitBoardRules.fromFen(fen), depth, evaluator, options, () -> false);
        return toLegalMove(bitMove, legal);
    }

    /** Depth = level / 2, plus 2 with at most 8 pieces left, plus 1 with at most 12. */
    static int searchDepth(Position position, int skillLevel) {
        int pieces = position.pieces().size();
        if (pieces <= 8) {
            return skillLevel / 2 + 2;
        }
        if (pieces <= 12) {
            return skillLevel / 2 + 1;
        }
        return skillLevel / 2;
    }

    /** Maps the search's {@link BitMove} onto the matching entry of the legal-move list. */
    private static ChessMove toLegalMove(BitMove bitMove, List<ChessMove> legal) {
        if (bitMove == null) {
            return null;
        }
        long both = bitMove.piece.position & bitMove.newPosition;
        int from = Long.numberOfTrailingZeros(bitMove.piece.position & ~both);
        int to = Long.numberOfTrailingZeros(bitMove.newPosition & ~both);
        ChessMove match = null;
        for (ChessMove m : legal) {
            if (m.from() != from || m.to() != to) {
                continue;
            }
            if (!m.isPromotion() || m.promotion() == Character.toLowerCase(bitMove.promotionChoice)) {
                return m;
            }
            match = m; // a promotion to another piece than the search picked; keep as fallback
        }
        return match;
    }
}
