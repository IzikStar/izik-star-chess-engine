package characterization;

import ai.BitBoard.BitBoard;
import ai.BoardState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checkmate and stalemate on unambiguous textbook positions.
 *
 * <p>If a bitboard-path assertion here fails on the first run, that failure is most
 * likely the "engine sometimes avoids mate / doesn't recognize draws" symptom from
 * the project history — move that method into {@link KnownBugsTest} with a note
 * rather than weakening the assertion (see docs/phase-0-notes.md).
 */
class CheckmateStalemateTest extends CharacterizationTestBase {

    // ---- Checkmate: OO path -------------------------------------------------

    @Test
    @DisplayName("OO path: fool's mate is checkmate (no moves, in check)")
    void ooFoolsMate() {
        BoardState b = oo(FOOLS_MATE);
        assertEquals(0, b.getLegalMoves().size(), "no legal moves");
        assertTrue(b.getIsCheck(), "side to move is in check");
        assertEquals(Integer.MAX_VALUE, b.getAccurateStatus(), "MAX_VALUE == checkmate");
        assertEquals(0, b.getStatus(), "0 == game over");
    }

    @Test
    @DisplayName("OO path: back-rank rook mate is checkmate")
    void ooBackRankMate() {
        BoardState b = oo(BACK_RANK_MATE);
        assertEquals(0, b.getLegalMoves().size());
        assertTrue(b.getIsCheck());
        assertEquals(Integer.MAX_VALUE, b.getAccurateStatus());
    }

    // ---- Checkmate: bitboard path ----------------------------------------

    @Test
    @DisplayName("Bitboard path: fool's mate -> MAX_VALUE (White to move, mated)")
    void bitFoolsMate() {
        BitBoard b = bit(FOOLS_MATE);
        assertTrue(b.getNextStates().isEmpty(), "no legal moves");
        assertEquals(Integer.MAX_VALUE, b.getStatus());
    }

    @Test
    @DisplayName("Bitboard path: back-rank mate -> MIN_VALUE (Black to move, mated)")
    void bitBackRankMate() {
        assertEquals(Integer.MIN_VALUE, bit(BACK_RANK_MATE).getStatus());
    }

    // ---- Stalemate --------------------------------------------------------

    @Test
    @DisplayName("OO path: K+Q vs K stalemate (game over, NOT check)")
    void ooStalemate() {
        BoardState b = oo(STALEMATE);
        assertEquals(0, b.getLegalMoves().size(), "no legal moves");
        assertFalse(b.getIsCheck(), "not in check");
        assertEquals(0, b.getAccurateStatus(), "0 == stalemate (game over, not mate)");
        assertEquals(0, b.getStatus());
    }

    @Test
    @DisplayName("Bitboard path: stalemate -> 0")
    void bitStalemate() {
        BitBoard b = bit(STALEMATE);
        assertTrue(b.getNextStates().isEmpty());
        assertEquals(0, b.getStatus());
    }

    // ---- Plain check is not game over -----------------------------------

    @Test
    @DisplayName("OO path: a simple check is status 2 (in check, not over)")
    void ooPlainCheck() {
        // White Ke1, Black Kh8 + Rook e8: White is in check along the e-file, but Ke1-d1/-f1/-d2/-f2 escape.
        BoardState b = oo("4r2k/8/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(b.getIsCheck());
        assertEquals(2, b.getAccurateStatus(), "2 == check, game continues");
        assertTrue(b.getLegalMoves().size() > 0);
    }
}
