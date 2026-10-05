package ai.board;

import java.util.List;

/** Test helpers for the search's boards. Squares: a8 is bit 0, h1 is bit 63. */
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

    /** The move that made {@code child}, as UCI ("e7e8q"). */
    static String uci(Board child) {
        int move = child.lastMove();
        char promotion = Move.promotion(move);
        return square(Move.from(move)) + square(Move.to(move)) + (promotion == 0 ? "" : String.valueOf(promotion));
    }

    /** The child of {@code board} reached by {@code uci}. */
    static Board child(Board board, String uci) {
        for (Board next : board.children()) {
            if (uci(next).equals(uci)) {
                return next;
            }
        }
        throw new AssertionError(uci + " is not among the search's moves");
    }

    /** Perft through the search's move generator, releasing each explored subtree. */
    static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }
        List<? extends Board> children = board.children();
        if (depth == 1) {
            return children.size();
        }
        long nodes = 0;
        for (Board child : children) {
            nodes += perft(child, depth - 1);
        }
        board.releaseChildren();
        return nodes;
    }
}
