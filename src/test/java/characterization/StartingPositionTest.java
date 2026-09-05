package characterization;

import ai.BoardState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The initial position: 20 legal moves, game in progress, nobody in check.
 * Locks the baseline for both rule paths.
 */
class StartingPositionTest extends CharacterizationTestBase {

    @Test
    @DisplayName("OO entry point: 20 legal moves from the initial position")
    void ooStartingMoveCount() {
        // Was a characterized bug: the old list-based getAllPossibleMovesForASide()
        // returned 12 (one-square pawn pushes + knights, no two-square push). Phase 2
        // routed BoardState.getLegalMoves() through the unified generator, so the OO and
        // bitboard entry points now agree.
        assertEquals(20, legalMovesOO(START));
        assertEquals(legalMovesBit(START), legalMovesOO(START));
    }

    @Test
    @DisplayName("Bitboard path: 20 legal moves from the initial position")
    void bitStartingMoveCount() {
        assertEquals(20, legalMovesBit(START));
    }

    @Test
    @DisplayName("OO path: initial position is in-progress, not check")
    void ooStartingStatus() {
        BoardState b = oo(START);
        assertEquals(1, b.getAccurateStatus(), "1 == normal/in-progress");
        assertEquals(1, b.getStatus(), "1 == game not over");
        assertFalse(b.getIsCheck());
        assertEquals(true, b.getIsWhiteToMove());
    }

    @Test
    @DisplayName("Bitboard path: initial position status is in-progress (1)")
    void bitStartingStatus() {
        assertEquals(1, bit(START).getStatus());
    }

    @Test
    @DisplayName("FEN round-trips through the OO board without changing the position field")
    void fenRoundTrip() {
        BoardState b = oo(START);
        // convertPiecesToFEN() rebuilds the whole FEN; only assert the piece-placement
        // field, since castling/clock serialization is known-quirky (see known-bug suite).
        String placement = b.convertPiecesToFEN().split(" ")[0];
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", placement);
    }
}
