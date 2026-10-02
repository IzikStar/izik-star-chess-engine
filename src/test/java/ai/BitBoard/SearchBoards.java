package ai.BitBoard;

/** Test helpers that read the search's boards directly. Squares: a8 is bit 0, h1 is bit 63. */
final class SearchBoards {

    private SearchBoards() {}

    /** The bit of a square such as "e4". */
    static long bit(String square) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';
        return 1L << ((7 - rank) * 8 + file);
    }

    static String square(int index) {
        return "" + (char) ('a' + (index & 7)) + (char) ('8' - (index >>> 3));
    }

    static long pieces(BitBoard b, boolean white) {
        return white
                ? b.whiteKings | b.whiteQueens | b.whiteRooks | b.whiteBishops | b.whiteKnights | b.whitePawns
                : b.blackKings | b.blackQueens | b.blackRooks | b.blackBishops | b.blackKnights | b.blackPawns;
    }

    /** The move from {@code parent} to {@code child} in UCI ("e2e4", "e1g1", "b7a8n"), read off the boards. */
    static String uci(BitBoard parent, BitBoard child) {
        boolean white = parent.isWhiteToMove;
        long before = pieces(parent, white);
        long after = pieces(child, white);
        long left = before & ~after;
        long arrived = after & ~before;
        long kings = white ? parent.whiteKings : parent.blackKings;
        if (Long.bitCount(left) == 2 && Long.bitCount(arrived) == 2 && (left & kings) != 0) {
            long kingAfter = white ? child.whiteKings : child.blackKings;
            return square(Long.numberOfTrailingZeros(left & kings)) + square(Long.numberOfTrailingZeros(kingAfter));
        }
        if (Long.bitCount(left) != 1 || Long.bitCount(arrived) != 1) {
            throw new AssertionError("not a single move: " + Long.bitCount(left) + " squares left, "
                    + Long.bitCount(arrived) + " filled");
        }
        int from = Long.numberOfTrailingZeros(left);
        int to = Long.numberOfTrailingZeros(arrived);
        long pawns = white ? parent.whitePawns : parent.blackPawns;
        String promotion = "";
        if ((pawns & left) != 0 && (to >>> 3) == (white ? 0 : 7)) {
            if (((white ? child.whiteQueens : child.blackQueens) & arrived) != 0) {
                promotion = "q";
            } else if (((white ? child.whiteRooks : child.blackRooks) & arrived) != 0) {
                promotion = "r";
            } else if (((white ? child.whiteBishops : child.blackBishops) & arrived) != 0) {
                promotion = "b";
            } else if (((white ? child.whiteKnights : child.blackKnights) & arrived) != 0) {
                promotion = "n";
            } else {
                throw new AssertionError("a pawn on the last rank without promoting");
            }
        }
        return square(from) + square(to) + promotion;
    }
}
