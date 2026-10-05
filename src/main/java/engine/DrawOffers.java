package engine;

import ai.board.Boards;
import ai.eval.Evaluators;
import ai.variant.Variant;
import ai.variant.Variants;

/**
 * Whether the engine takes a draw a human offers: only when it is not better. The rule is the
 * static evaluation of the position from the engine's side: it accepts at +0.3 pawns or less.
 * (A static evaluation misses tactics, but a missed tactic makes the engine think it is better
 * than it is, so the mistakes go towards declining.)
 */
public final class DrawOffers {

    /** The classic weights count a pawn as 100 (centipawns), so 0.3 pawns is 30. */
    static final int ACCEPT_AT_MOST = 30;

    private DrawOffers() {}

    /** True if the engine, playing {@code engineIsWhite}'s side in {@code fen}, accepts a draw. */
    public static boolean engineAccepts(String fen, boolean engineIsWhite) {
        return engineAccepts(Variants.CHESS, fen, engineIsWhite);
    }

    /** Like {@link #engineAccepts(String, boolean)} in a game of {@code variant}. */
    public static boolean engineAccepts(Variant variant, String fen, boolean engineIsWhite) {
        return engineScore(variant, fen, engineIsWhite) <= ACCEPT_AT_MOST;
    }

    /** The static evaluation from the engine's side (positive: the engine is better). */
    static int engineScore(String fen, boolean engineIsWhite) {
        return engineScore(Variants.CHESS, fen, engineIsWhite);
    }

    static int engineScore(Variant variant, String fen, boolean engineIsWhite) {
        var evaluator = Evaluators.usesChessEvaluation(variant) ? Weights.DEFAULT.evaluator() : Evaluators.forVariant(variant);
        return evaluator.evaluate(Boards.fromFen(variant, fen), engineIsWhite ? 0 : 1);
    }
}
