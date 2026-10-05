package ai.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #7 (docs/phase-4b-research.md §2.3): after a double pawn push, the search must take the
 * square the pawn passed over as the en-passant square. The rules facade works the square out
 * from the move itself, so only the search's own boards can show this bug.
 */
class SearchEnPassantTest {

    private static final long EIGHTH_RANK = 0xFFL;

    private static long pawns(Board board, int player) {
        return ((ChessPosition) board).pieces(player, ChessPosition.PAWN);
    }

    @Test
    @DisplayName("After ...d7-d5 the search lets White take en passant, exd6")
    void whiteTakesEnPassant() {
        Board pushed = SearchBoards.child(Boards.fromFen("4k3/3p4/8/4P3/8/8/8/4K3 b - - 0 1"), "d7d5");
        assertEquals(0, pawns(SearchBoards.child(pushed, "e5d6"), 1), "the d5 pawn is taken");
    }

    @Test
    @DisplayName("After e2-e4 the search lets Black take en passant, dxe3")
    void blackTakesEnPassant() {
        Board pushed = SearchBoards.child(Boards.fromFen("4k3/8/8/8/3p4/8/4P3/4K3 w - - 0 1"), "e2e4");
        assertEquals(0, pawns(SearchBoards.child(pushed, "d4e3"), 0), "the e4 pawn is taken");
    }

    @Test
    @DisplayName("No pawn lands on the last rank without promoting (position 4 mirrored, after ...d5)")
    void noEnPassantOntoTheLastRank() {
        Board pushed = SearchBoards.child(Boards.fromFen(
                "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"), "d7d5");
        for (Board next : pushed.children()) {
            assertEquals(0, pawns(next, 0) & EIGHTH_RANK, "a white pawn stands on the eighth rank");
        }
    }
}
