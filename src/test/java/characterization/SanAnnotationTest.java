package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Game;
import rules.MoveResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The move-string status suffix ("+", "#"). Phase 2 moved it off the live-board
 * simulate-and-revert; Phase 3 moved SAN itself into the headless {@code rules.San}, reported
 * through {@link MoveResult#san()}.
 */
class SanAnnotationTest extends CharacterizationTestBase {

    private static MoveResult play(String fen, String uci) {
        return new Game(fen).play(ChessMove.fromUci(uci));
    }

    @Test
    @DisplayName("a checking (non-mating) move gets a '+' suffix")
    void checkGetsPlus() {
        // White Ra1, black Kg8 with luft on h7 (h-pawn already on h6). Ra1-a8+, King runs to h7.
        String san = play("6k1/5pp1/7p/8/8/8/8/R3K3 w Q - 0 1", "a1a8").san();
        assertTrue(san.endsWith("+"), san);
        assertFalse(san.contains("#"), san);
    }

    @Test
    @DisplayName("a mating move gets a '#' suffix and ends the game as a win")
    void mateGetsHash() {
        // Back-rank mate: black Kg8 boxed by f7/g7/h7, white Ra1-a8#.
        MoveResult r = play("6k1/5ppp/8/8/8/8/8/R3K3 w Q - 0 1", "a1a8");
        assertEquals("Ra8#", r.san());
        assertEquals(rules.GameStatus.CHECKMATE, r.status());
        assertTrue(r.whiteMoved());
    }

    @Test
    @DisplayName("a quiet move gets no status suffix")
    void quietMoveNoSuffix() {
        assertEquals("e4", play(START, "e2e4").san());
    }
}
