package characterization;

import ai.BoardState;
import main.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pieces.Piece;

import java.util.ConcurrentModificationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Castling, en passant and promotion as seen by the object-oriented rule path.
 *
 * <p>The reliable OO entry point is {@code BoardState.isValidMove}. The bulk generator
 * {@code getAllPossibleMovesForASide()} is dead code (no live callers) and is
 * characterized here as broken, not used to assert anything.
 */
class SpecialMovesTest extends CharacterizationTestBase {

    @Test
    @DisplayName("Castling: OO isValidMove accepts both O-O and O-O-O")
    void castlingIsValidMove() {
        BoardState b = oo(CASTLING_OPEN);
        Piece king = b.getPiece(4, 7);
        assertTrue(b.isValidMove(new Move(b, king, 6, 7)), "kingside e1-g1");
        assertTrue(b.isValidMove(new Move(b, king, 2, 7)), "queenside e1-c1");
    }

    @Test
    @DisplayName("En passant: OO isValidMove accepts e5xf6 e.p.")
    void enPassantIsValidMove() {
        BoardState b = oo(EN_PASSANT);
        Piece whitePawnE5 = b.getPiece(4, 3);
        assertTrue(b.isValidMove(new Move(b, whitePawnE5, 5, 2)));
    }

    @Test
    @DisplayName("Promotion: OO isValidMove accepts the a7-a8 push")
    void promotionIsValidMove() {
        BoardState b = oo(PROMOTION);
        Piece pawn = b.getPiece(0, 1);
        assertTrue(b.isValidMove(new Move(b, pawn, 0, 0)));
    }

    @Test
    @DisplayName("CHARACTERIZED BUG: getAllPossibleMovesForASide() throws CME when a capture exists")
    void bulkGeneratorThrowsOnCapture() {
        // makeMoveToCheckIt() calls capture()/loadPiecesFromFen(), which structurally
        // mutate pieceList while getAllPossibleMovesForASide() is iterating it. Any
        // position where the side to move has a capture available triggers this.
        assertThrows(ConcurrentModificationException.class,
                () -> oo(CASTLING_OPEN).getAllPossibleMovesForASide());
    }
}
