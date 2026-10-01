package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Rules;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Castling, en passant and promotion are legal through the game API. */
class SpecialMovesTest extends CharacterizationTestBase {

    @Test
    @DisplayName("Castling: both O-O and O-O-O are legal")
    void castlingIsLegal() {
        assertTrue(legal(CASTLING_OPEN, "e1g1"), "kingside e1-g1");
        assertTrue(legal(CASTLING_OPEN, "e1c1"), "queenside e1-c1");
    }

    @Test
    @DisplayName("En passant: e5xf6 e.p. is legal")
    void enPassantIsLegal() {
        assertTrue(legal(EN_PASSANT, "e5f6"));
    }

    @Test
    @DisplayName("Promotion: the a7-a8 push is legal")
    void promotionIsLegal() {
        assertTrue(legal(PROMOTION, "a7a8q"));
    }

    @Test
    @DisplayName("Castling and en passant appear in the legal-move list")
    void specialMovesInLegalMoveList() {
        assertTrue(Rules.legalMoves(CASTLING_OPEN).stream()
                .anyMatch(m -> m.toUci().equals("e1g1")), "O-O in the list");
        assertTrue(Rules.legalMoves(EN_PASSANT).stream()
                .anyMatch(m -> m.toUci().equals("e5f6")), "e.p. in the list");
    }
}
