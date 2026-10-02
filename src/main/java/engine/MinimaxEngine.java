package engine;

import ai.BitBoard.BitBoardRules;
import ai.BitBoard.BitMove;
import ai.Minimax;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.util.List;
import java.util.Random;

/**
 * The built-in engine: a random legal move at level 0, otherwise the bitboard minimax search at a
 * depth derived from the level and the game stage (the formula {@code ai.myEngine} used).
 */
public final class MinimaxEngine implements Engine {

    private final Random random;

    public MinimaxEngine() {
        this(new Random());
    }

    public MinimaxEngine(Random random) {
        this.random = random;
    }

    @Override
    public ChessMove bestMove(String fen, int skillLevel) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        if (skillLevel <= 0) {
            return legal.get(random.nextInt(legal.size()));
        }
        return searchAtDepth(fen, Math.max(1, searchDepth(Position.fromFen(fen), skillLevel)));
    }

    /** The minimax search's move at an explicit depth, or {@code null} if there is no legal move. */
    public static ChessMove searchAtDepth(String fen, int depth) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        if (legal.isEmpty()) {
            return null;
        }
        BitMove bitMove = Minimax.getBestMove(BitBoardRules.fromFen(fen), depth);
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
