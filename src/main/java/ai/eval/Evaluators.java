package ai.eval;

import ai.piece.Grid;
import ai.piece.StandardPieces;
import ai.variant.Variant;

/** Which evaluation a variant is played with (Phase 6 R4c). */
public final class Evaluators {

    private Evaluators() {}

    /**
     * Games with the chess pieces on a chess board where losing the king loses (chess, King of the
     * Hill, Three-check) keep the tuned chess evaluation, so the difficulty ladder stays as
     * calibrated; every other variant gets an evaluation built from its pieces.
     */
    public static Evaluator forVariant(Variant variant) {
        if (usesChessEvaluation(variant)) {
            return ChessEvaluate.DEFAULT;
        }
        return PieceSetEvaluate.of(variant);
    }

    public static boolean usesChessEvaluation(Variant variant) {
        return variant.pieces().equals(StandardPieces.ALL) && variant.grid().equals(Grid.CHESS)
                && variant.goal() != Variant.Goal.LOSE_EVERYTHING;
    }
}
