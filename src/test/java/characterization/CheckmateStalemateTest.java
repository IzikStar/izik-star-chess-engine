package characterization;

import ai.BitBoard.BitBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.GameStatus;
import rules.Rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checkmate, stalemate and plain check, through the game API and the bitboard. */
class CheckmateStalemateTest extends CharacterizationTestBase {

    // ---- Checkmate: game API ------------------------------------------------

    @Test
    @DisplayName("Game API: fool's mate is checkmate (no moves, in check)")
    void apiFoolsMate() {
        assertEquals(0, legalMovesApi(FOOLS_MATE), "no legal moves");
        assertTrue(Rules.isCheck(FOOLS_MATE), "side to move is in check");
        assertEquals(GameStatus.CHECKMATE, Rules.status(FOOLS_MATE));
        assertTrue(Rules.status(FOOLS_MATE).isGameOver());
    }

    @Test
    @DisplayName("Game API: back-rank rook mate is checkmate")
    void apiBackRankMate() {
        assertEquals(0, legalMovesApi(BACK_RANK_MATE));
        assertTrue(Rules.isCheck(BACK_RANK_MATE));
        assertEquals(GameStatus.CHECKMATE, Rules.status(BACK_RANK_MATE));
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
    @DisplayName("Game API: K+Q vs K stalemate (game over, NOT check)")
    void apiStalemate() {
        assertEquals(0, legalMovesApi(STALEMATE), "no legal moves");
        assertFalse(Rules.isCheck(STALEMATE), "not in check");
        assertEquals(GameStatus.STALEMATE, Rules.status(STALEMATE));
        assertTrue(Rules.status(STALEMATE).isDraw());
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
    @DisplayName("Game API: a simple check is CHECK (in check, not over)")
    void apiPlainCheck() {
        // White Ke1, Black Kh8 + Rook e8: White is in check along the e-file, but Ke1-d1/-f1/-d2/-f2 escape.
        String fen = "4r2k/8/8/8/8/8/8/4K3 w - - 0 1";
        assertTrue(Rules.isCheck(fen));
        assertEquals(GameStatus.CHECK, Rules.status(fen));
        assertTrue(legalMovesApi(fen) > 0);
    }
}
