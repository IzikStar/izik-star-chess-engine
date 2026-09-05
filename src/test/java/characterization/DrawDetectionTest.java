package characterization;

import ai.BitBoard.BitBoard;
import ai.BoardState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Draw detection — the four bugs from the project history (commit ad9ca32, "my engine doesn't
 * recognize draws"), now FIXED in Phase 2 increment 3 and pinned green.
 *
 * <p>These were {@code KnownBugsTest} (@Tag("known-bug"), red on purpose, run via
 * {@code mvn test -Pknown-bugs}). Increment 3 routed {@code BoardState}'s status through the
 * unified {@code rules.Rules} authority, fixed the FEN half-move-clock parse, and corrected the
 * bitboard's per-ply 50-move threshold — so they assert the correct answer and pass in the
 * default suite. See docs/phase-2-research.md §8.
 */
class DrawDetectionTest extends CharacterizationTestBase {

    @Test
    @DisplayName("FEN half-move clock is parsed in full, not truncated to its first digit")
    void fenHalfMoveClockParsedInFull() {
        assertEquals(50, oo("k7/8/8/8/8/8/8/7K w - - 50 100").numOfTurnWithoutCaptureOrPawnMove,
                "half-move clock \"50\" must load as 50");
        assertEquals(13, oo("k7/8/8/8/8/8/8/7K w - - 13 30").numOfTurnWithoutCaptureOrPawnMove,
                "half-move clock \"13\" must load as 13");
    }

    @Test
    @DisplayName("OO status path applies the 50-move rule")
    void ooPathAppliesFiftyMoveRule() {
        BoardState b = oo(START);
        b.numOfTurnWithoutCaptureOrPawnMove = 200; // 100 full moves without progress
        assertEquals(0, b.getAccurateStatus(),
                "getAccurateStatus() reports a draw (0) after 100 moves without progress");
    }

    @Test
    @DisplayName("Bitboard declares the 50-move draw at 100 plies (50 full moves), not 50 plies")
    void bitboardFiftyMoveThresholdIsCorrect() {
        BoardState b = oo(START);
        b.numOfTurnWithoutCaptureOrPawnMove = 70; // 35 full moves -> NOT a draw yet
        assertEquals(1, new BitBoard(b).getStatus(),
                "still in progress at 35 moves");
        b.numOfTurnWithoutCaptureOrPawnMove = 100; // 50 full moves -> draw
        assertEquals(0, new BitBoard(b).getStatus(),
                "draw at 50 full moves");
    }

    @Test
    @DisplayName("OO status path recognises a lone-king vs lone-king draw")
    void ooPathRecognisesInsufficientMaterial() {
        BoardState b = oo("k7/8/8/8/8/8/8/7K w - - 0 1");
        assertEquals(0, b.getAccurateStatus(),
                "K vs K is a dead position; getAccurateStatus() == 0");
    }
}
