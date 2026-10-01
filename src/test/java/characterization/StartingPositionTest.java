package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Game;
import rules.GameStatus;
import rules.Position;
import rules.Rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartingPositionTest extends CharacterizationTestBase {

    @Test
    @DisplayName("Game API: 20 legal moves from the initial position, same as the bitboard")
    void apiStartingMoveCount() {
        // Was a characterized bug: the old list-based getAllPossibleMovesForASide() returned 12.
        assertEquals(20, legalMovesApi(START));
        assertEquals(legalMovesBit(START), legalMovesApi(START));
    }

    @Test
    @DisplayName("Bitboard path: 20 legal moves from the initial position")
    void bitStartingMoveCount() {
        assertEquals(20, legalMovesBit(START));
    }

    @Test
    @DisplayName("Game API: initial position is in progress, not check, White to move")
    void apiStartingStatus() {
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(START));
        assertFalse(Rules.isCheck(START));
        assertTrue(Position.fromFen(START).whiteToMove());
    }

    @Test
    @DisplayName("Bitboard path: initial position status is in-progress (1)")
    void bitStartingStatus() {
        assertEquals(1, bit(START).getStatus());
    }

    @Test
    @DisplayName("FEN round-trips through the game unchanged")
    void fenRoundTrip() {
        assertEquals(START, new Game(START).fen());
        assertEquals(START, Position.fromFen(START).toFen());
    }
}
