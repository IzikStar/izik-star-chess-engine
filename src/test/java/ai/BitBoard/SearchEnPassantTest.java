package ai.BitBoard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #7 (docs/phase-4b-research.md §2.3): after a double pawn push, the search must take the
 * square the pawn passed over as the en-passant square. The rules facade works the square out
 * from the move itself, so only the search's own boards can show this bug.
 */
class SearchEnPassantTest {

    private static final long EIGHTH_RANK = 0xFFL;

    /** The bit of a square such as "e4" (a8 is bit 0, h1 is bit 63). */
    static long bit(String square) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';
        return 1L << ((7 - rank) * 8 + file);
    }

    static long pieces(BitBoard b, boolean white) {
        return white
                ? b.whiteKings | b.whiteQueens | b.whiteRooks | b.whiteBishops | b.whiteKnights | b.whitePawns
                : b.blackKings | b.blackQueens | b.blackRooks | b.blackBishops | b.blackKnights | b.blackPawns;
    }

    /** The search's next board in which the side to move took its piece from {@code from} to {@code to}. */
    static BitBoard child(BitBoard board, String from, String to) {
        boolean white = board.getIsWhiteToMove();
        long before = pieces(board, white);
        for (BitBoard next : board.getNextStates()) {
            long after = pieces(next, white);
            if ((before & ~after) == bit(from) && (after & ~before) == bit(to)) {
                return next;
            }
        }
        throw new AssertionError(from + to + " is not among the search's moves");
    }

    @Test
    @Tag("known-bug")
    @DisplayName("After ...d7-d5 the search lets White take en passant, exd6")
    void whiteTakesEnPassant() {
        BitBoard pushed = child(BitBoardRules.fromFen("4k3/3p4/8/4P3/8/8/8/4K3 b - - 0 1"), "d7", "d5");
        assertEquals(0, child(pushed, "e5", "d6").blackPawns, "the d5 pawn is taken");
    }

    @Test
    @Tag("known-bug")
    @DisplayName("After e2-e4 the search lets Black take en passant, dxe3")
    void blackTakesEnPassant() {
        BitBoard pushed = child(BitBoardRules.fromFen("4k3/8/8/8/3p4/8/4P3/4K3 w - - 0 1"), "e2", "e4");
        assertEquals(0, child(pushed, "d4", "e3").whitePawns, "the e4 pawn is taken");
    }

    @Test
    @Tag("known-bug")
    @DisplayName("No pawn lands on the last rank without promoting (position 4 mirrored, after ...d5)")
    void noEnPassantOntoTheLastRank() {
        BitBoard pushed = child(BitBoardRules.fromFen(
                "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"), "d7", "d5");
        for (BitBoard next : pushed.getNextStates()) {
            assertEquals(0, next.whitePawns & EIGHTH_RANK, "a white pawn stands on the eighth rank");
        }
    }
}
