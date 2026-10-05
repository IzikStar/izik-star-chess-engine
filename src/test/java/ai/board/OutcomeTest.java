package ai.board;

import ai.variant.Variant;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 R4a: how each variant's goal ends a game, seen by the player to move. */
class OutcomeTest {

    private static Outcome outcome(Variant variant, String fen) {
        return GenericBoard.fromFen(BoardRules.of(variant), fen).outcome();
    }

    @Test
    @DisplayName("Chess: mated loses, stalemated draws, 50 moves draw")
    void chess() {
        assertEquals(Outcome.LOSS, outcome(Variants.CHESS, "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"));
        assertEquals(Outcome.DRAW, outcome(Variants.CHESS, "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1"));
        assertEquals(Outcome.DRAW, outcome(Variants.CHESS, "4k3/8/8/8/8/8/8/4K2R w K - 100 80"));
        assertEquals(Outcome.ONGOING, outcome(Variants.CHESS, Variants.CHESS.startFen()));
    }

    @Test
    @DisplayName("Antichess: no pieces left or no legal move wins")
    void antichess() {
        assertEquals(Outcome.WIN, outcome(Variants.ANTICHESS, "8/8/8/8/8/8/8/k7 w - - 0 30"));
        // White's pawn is blocked by Black's: White cannot move, so White wins
        assertEquals(Outcome.WIN, outcome(Variants.ANTICHESS, "8/8/8/8/8/p7/P7/8 w - - 0 30"));
        // a king is no royal piece: it may stand attacked and be taken
        GenericBoard board = GenericBoard.fromFen(BoardRules.of(Variants.ANTICHESS), "8/8/8/8/8/8/1q6/K7 w - - 0 30");
        assertEquals(1, board.children().size(), "the king must take the queen");
        assertEquals(Outcome.ONGOING, board.outcome());
    }

    @Test
    @DisplayName("King of the Hill: a king on a centre square has won")
    void kingOfTheHill() {
        assertEquals(Outcome.LOSS, outcome(Variants.KING_OF_THE_HILL, "4k3/8/8/8/4K3/8/8/8 b - - 1 40"));
        assertTrue(GenericBoard.fromFen(BoardRules.of(Variants.KING_OF_THE_HILL), "4k3/8/8/8/4K3/8/8/8 b - - 1 40")
                .children().isEmpty(), "no moves once the game is over");
        assertEquals(Outcome.ONGOING, outcome(Variants.KING_OF_THE_HILL, "4k3/8/8/8/8/4K3/8/8 b - - 1 40"));
    }

    @Test
    @DisplayName("Three-check: the third check wins, and checks are counted move by move")
    void threeCheck() {
        assertEquals(Outcome.LOSS, outcome(Variants.THREE_CHECK, "4k3/8/8/8/8/8/8/4K2R b K - 0+3 1 20"));
        GenericBoard board = GenericBoard.fromFen(BoardRules.of(Variants.THREE_CHECK), "4k3/8/8/8/8/8/8/R3K3 w - - 1+3 0 20");
        Board check = SearchBoards.child(board, "a1a8");
        assertEquals(Outcome.LOSS, check.outcome(), check.toFen());
        assertEquals("R3k3/8/8/8/8/8/8/4K3 b - - 0+3 1 20", check.toFen());
    }
}
