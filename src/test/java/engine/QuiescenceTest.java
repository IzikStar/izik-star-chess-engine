package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The search plays on through captures past its depth (Phase 5 step 2), so even at depth 1 it
 * sees a piece being taken back.
 */
class QuiescenceTest {

    @Test
    @DisplayName("At depth 1 the queen does not take a pawn that a pawn defends")
    void doesNotGrabADefendedPawn() {
        assertNotEquals("d1d5", MinimaxEngine.searchAtDepth("k7/8/2p5/3p4/8/8/8/K2Q4 w - - 0 1", 1).toUci());
    }

    @Test
    @DisplayName("At depth 1 the queen still takes a knight nobody defends")
    void takesAFreePiece() {
        assertEquals("d1d5", MinimaxEngine.searchAtDepth("k7/8/8/3n4/8/8/8/K2Q4 w - - 0 1", 1).toUci());
    }
}
