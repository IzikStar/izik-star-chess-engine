package characterization;

import ai.BoardState;
import main.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pieces.Piece;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SAN move-string status suffix ("+", "#", "1/2-1/2"). Phase 2 increment 4 moved this off
 * the live-board simulate-and-revert ({@code BoardState.makeMoveAndGetStatus}, now deleted) onto
 * {@code rules.Rules} querying the position after the move. These pin that it still annotates.
 */
class SanAnnotationTest extends CharacterizationTestBase {

    private static String san(BoardState b, int fromCol, int fromRow, int toCol, int toRow) {
        Piece p = b.getPiece(fromCol, fromRow);
        Move m = new Move(b, p, toCol, toRow);
        assertTrue(b.isValidMove(m), "move should be legal");
        m.setRepresentation();
        return m.getRepresentation();
    }

    @Test
    @DisplayName("a checking (non-mating) move gets a '+' suffix")
    void checkGetsPlus() {
        // White Ra1, black Kg8 with luft on h7 (h-pawn already on h6). Ra1-a8+, King runs to h7.
        BoardState b = oo("6k1/5pp1/7p/8/8/8/8/R3K3 w Q - 0 1");
        String rep = san(b, 0, 7, 0, 0);
        assertTrue(rep.contains("+"), rep);
        assertFalse(rep.contains("#"), rep);
    }

    @Test
    @DisplayName("a mating move gets a '#' suffix and the result")
    void mateGetsHash() {
        // Back-rank mate: black Kg8 boxed by f7/g7/h7, white Ra1-a8#.
        BoardState b = oo("6k1/5ppp/8/8/8/8/8/R3K3 w Q - 0 1");
        String rep = san(b, 0, 7, 0, 0);
        assertTrue(rep.contains("#"), rep);
        assertTrue(rep.contains("1-0"), rep);
    }

    @Test
    @DisplayName("a quiet move gets no status suffix")
    void quietMoveNoSuffix() {
        String rep = san(oo(START), 4, 6, 4, 4); // 1. e4
        assertFalse(rep.contains("+"), rep);
        assertFalse(rep.contains("#"), rep);
    }
}
