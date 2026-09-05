package characterization;

import ai.BoardState;
import main.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pieces.Piece;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Castling, en passant and promotion through the object-oriented entry point
 * ({@code BoardState.isValidMove} / {@code getLegalMoves}), which since Phase 2 delegates to
 * the unified {@code rules.Rules} authority.
 *
 * <p>The old characterized bug here — {@code getAllPossibleMovesForASide()} throwing
 * {@code ConcurrentModificationException} whenever a capture was available — is gone with that
 * method (Phase 2 increment 5); nothing to assert.
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
    @DisplayName("Castling and en passant appear in the OO legal-move list")
    void specialMovesInLegalMoveList() {
        assertTrue(oo(CASTLING_OPEN).getLegalMoves().stream()
                .anyMatch(m -> m.toUci().equals("e1g1")), "O-O in the list");
        assertTrue(oo(EN_PASSANT).getLegalMoves().stream()
                .anyMatch(m -> m.toUci().equals("e5f6")), "e.p. in the list");
    }
}
