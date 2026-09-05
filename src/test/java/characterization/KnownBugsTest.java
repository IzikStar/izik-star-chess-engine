package characterization;

import ai.BitBoard.BitBoard;
import ai.BoardState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * KNOWN BUGS — documented, named, and RED on purpose.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 * <pre>  mvn test -Pknown-bugs  </pre>
 *
 * <p>Each test asserts the <em>correct</em> chess/engine behavior. It fails today.
 * When a later phase fixes the underlying cause, the test goes green — at which point
 * drop the {@code @Tag("known-bug")} and move it into the appropriate green suite.
 * These correspond to the "doesn't recognize draws" complaint in the project history
 * (commit ad9ca32). See docs/phase-0-notes.md for the full list and the ones that
 * still need a runnable engine to reproduce ("sometimes avoids checkmate").
 */
@Tag("known-bug")
class KnownBugsTest extends CharacterizationTestBase {

    @Test
    @DisplayName("BUG: FEN half-move clock is truncated to its first digit on load")
    void fenHalfMoveClockTruncated() {
        // BoardState.loadPiecesFromFen: numOfTurnWithoutCaptureOrPawnMove =
        //   Character.getNumericValue(parts[4].charAt(0))  -> reads ONE character.
        assertEquals(50, oo("k7/8/8/8/8/8/8/7K w - - 50 100").numOfTurnWithoutCaptureOrPawnMove,
                "half-move clock \"50\" is loaded as 5");
        assertEquals(13, oo("k7/8/8/8/8/8/8/7K w - - 13 30").numOfTurnWithoutCaptureOrPawnMove,
                "half-move clock \"13\" is loaded as 1");
    }

    @Test
    @DisplayName("BUG: OO status path never applies the 50-move rule")
    void ooPathIgnoresFiftyMoveRule() {
        BoardState b = oo(START);
        b.numOfTurnWithoutCaptureOrPawnMove = 200; // 100 full moves without progress
        assertEquals(0, b.getAccurateStatus(),
                "getAccurateStatus() should report a draw (0); it returns 1 (in progress)");
    }

    @Test
    @DisplayName("BUG: bitboard declares the 50-move draw at half the required count")
    void bitboardFiftyMoveThresholdTooLow() {
        // BitBoard.getStatus(): numOfTurnsWithoutCaptureOrPawnMove >= 50, where the
        // counter is incremented once per ply -> triggers after 25 full moves.
        BoardState b = oo(START);
        b.numOfTurnWithoutCaptureOrPawnMove = 70; // 35 full moves -> NOT a draw yet
        assertEquals(1, new BitBoard(b).getStatus(),
                "still in progress at 35 moves; bitboard returns 0 (claims draw)");
    }

    @Test
    @DisplayName("BUG: OO status path does not recognise a lone-king vs lone-king draw")
    void ooPathIgnoresInsufficientMaterial() {
        // insufficientMaterial() exists and returns true here, but no status method calls it.
        BoardState b = oo("k7/8/8/8/8/8/8/7K w - - 0 1");
        assertEquals(0, b.getAccurateStatus(),
                "K vs K is a dead position; getAccurateStatus() returns 1");
    }
}
