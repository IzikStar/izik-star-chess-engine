package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.GameStatus;
import rules.Position;
import rules.Rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The four draw-detection bugs Phase 2 fixed (they were {@code KnownBugsTest}, red under
 * {@code -Pknown-bugs}). Phase 3 re-pointed the old {@code BoardState} assertions at the game API.
 */
class DrawDetectionTest extends CharacterizationTestBase {

    @Test
    @DisplayName("FEN half-move clock is parsed in full, not truncated to its first digit")
    void fenHalfMoveClockParsedInFull() {
        assertEquals(50, Position.fromFen("k7/8/8/8/8/8/8/7K w - - 50 100").halfmoveClock());
        assertEquals(13, Position.fromFen("k7/8/8/8/8/8/8/7K w - - 13 30").halfmoveClock());
    }

    @Test
    @DisplayName("Game API applies the 50-move rule")
    void apiAppliesFiftyMoveRule() {
        assertEquals(GameStatus.DRAW_FIFTY_MOVE,
                Rules.status("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 200 101"));
    }

    @Test
    @DisplayName("Bitboard declares the 50-move draw at 100 plies (50 full moves), not 50 plies")
    void bitboardFiftyMoveThresholdIsCorrect() {
        assertEquals(1, bit("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 70 36").getStatus(),
                "still in progress at 35 moves");
        assertEquals(0, bit("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 100 51").getStatus(),
                "draw at 50 full moves");
    }

    @Test
    @DisplayName("Game API recognises a lone-king vs lone-king draw")
    void apiRecognisesInsufficientMaterial() {
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("k7/8/8/8/8/8/8/7K w - - 0 1"));
    }
}
