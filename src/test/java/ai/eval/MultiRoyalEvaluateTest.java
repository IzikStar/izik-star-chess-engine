package ai.eval;

import ai.board.Board;
import ai.board.Boards;
import ai.piece.Grid;
import ai.piece.StandardPieces;
import ai.variant.CastlingRule;
import ai.variant.Variant;
import ai.variant.WinCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Variants where a side has several royal pieces: the evaluation looks at every one of them. */
class MultiRoyalEvaluateTest {

    /** White: kings on b1 and g1, each behind three pawns. Black: one king, no pawns. */
    static final String TWO_SHELTERED = "4k3/8/8/8/8/8/PPP2PPP/1K4K1 w - - 0 1";
    static final String ONE_SHELTERED = "4k3/8/8/8/8/8/5PPP/6K1 w - - 0 1";

    static Variant twoKings(String fen, Variant.RoyalMode mode) {
        return new Variant("two-kings", "Two kings", StandardPieces.ALL, Grid.CHESS, fen,
                List.of(WinCondition.checkmate()), mode, Variant.Stalemate.DRAW, true, 50, false, CastlingRule.NONE);
    }

    /** The chess evaluation with every weight 0 except {@code name} (middlegame and endgame) set to {@code value}. */
    static ChessEvaluate only(String name, int value) {
        ParamVector p = new ParamVector(ChessEvaluate.SCHEMA, new int[ChessEvaluate.SCHEMA.size()]);
        if (name != null) {
            p = p.with(name + ".mg", value).with(name + ".eg", value);
        }
        return new ChessEvaluate(p);
    }

    static int eval(Evaluator e, String fen, Variant.RoyalMode mode) {
        Board b = Boards.fromFen(twoKings(fen, mode), fen);
        return e.evaluate(b, 0);
    }

    @Test
    @DisplayName("king safety counts the shelter of every king, not only the first")
    void shelterPerKing() {
        ChessEvaluate e = only("kingSafety.shieldNear", 10);
        assertEquals(30, eval(e, ONE_SHELTERED, Variant.RoyalMode.ALL_SAFE));
        assertEquals(60, eval(e, TWO_SHELTERED, Variant.RoyalMode.ALL_SAFE));
    }

    @Test
    @DisplayName("an attacker near either king counts")
    void attackersPerKing() {
        ChessEvaluate e = only("kingSafety.knightAttacker", 10);
        // a black knight on c3 reaches b1's zone (a2, b1 ... d1) only; on h3 it reaches g1's zone (g1, f2)
        assertEquals(-10, eval(e, "4k3/8/8/8/8/2n5/8/1K4K1 w - - 0 1", Variant.RoyalMode.ALL_SAFE));
        assertEquals(-10, eval(e, "4k3/8/8/8/8/7n/8/1K4K1 w - - 0 1", Variant.RoyalMode.ALL_SAFE));
    }

    @Test
    @DisplayName("last standing: a spare king is worth something; all safe: kings cannot be taken, so it is not")
    void spareKing() {
        ChessEvaluate e = only(null, 0);
        assertEquals(Evaluators.SPARE_ROYAL, eval(e, TWO_SHELTERED, Variant.RoyalMode.LAST_STANDING));
        assertEquals(0, eval(e, TWO_SHELTERED, Variant.RoyalMode.ALL_SAFE));
        // the same seen from Black
        Board b = Boards.fromFen(twoKings(TWO_SHELTERED, Variant.RoyalMode.LAST_STANDING), TWO_SHELTERED);
        assertEquals(-Evaluators.SPARE_ROYAL, e.evaluate(b, 1));
    }

    @Test
    @DisplayName("the evaluation built from pieces values royal pieces as spares only in last standing")
    void pieceSetSpareRoyal() {
        assertEquals(Evaluators.SPARE_ROYAL, PieceSetEvaluate.schema(twoKings(TWO_SHELTERED, Variant.RoyalMode.LAST_STANDING))
                .defaults().get("material.king"));
        assertEquals(0, PieceSetEvaluate.schema(twoKings(TWO_SHELTERED, Variant.RoyalMode.ALL_SAFE))
                .defaults().get("material.king"));
    }
}
