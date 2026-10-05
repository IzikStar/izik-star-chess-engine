package ai.board;

import java.util.List;

/**
 * A game position as the search, the evaluation and the rules see it (Phase 6, docs/phase-6-research.md).
 * Nothing outside the board implementation knows how a position is stored, so another board (other
 * pieces, other rules, later another geometry) can stand in without touching the search.
 *
 * <p>Positions are immutable apart from a cache of their children: a move makes a new position.
 * Players are numbered in turn order from 0 (chess: 0 = White, 1 = Black); squares are the board's
 * own ids ({@link Move}).
 */
public interface Board {

    /** The player whose move it is, counted from 0 in turn order. */
    int sideToMove();

    /**
     * The positions after each legal move, in the move generator's order. Kept until
     * {@link #releaseChildren()}; callers must not change the list.
     */
    List<? extends Board> children();

    /**
     * The same positions as {@link #children()} in a new list the caller may reorder, the generator's
     * guess at the best moves first (for chess: checks and captures). The search orders further from
     * here.
     */
    List<Board> orderedChildren();

    /**
     * The captures and promotions worth playing on once the search depth has run out (quiescence),
     * best first, in a new list. Not cached, and leaves {@link #children()} untouched.
     */
    List<Board> noisyChildren();

    /** Drops the cached children, so a searched subtree can be garbage collected. */
    void releaseChildren();

    /** The move that led to this position from its parent ({@link Move}), or {@link Move#NONE} at a root. */
    int lastMove();

    /**
     * For a child of {@code parent}: a score for trying it early when it captures or promotes (higher
     * is better, never negative), or a negative number for a quiet move.
     */
    int captureScore(Board parent);

    /** True when the player to move is under attack in a way that must be answered (chess: check). */
    boolean inCheck();

    /**
     * Whether the game is over here and how, for the player to move: by the variant's goal (a
     * royal piece on the hill, the last check, no pieces left), by having no legal move, or by a draw
     * rule the board knows (50 moves). Repetition is the game's to judge, not the position's.
     */
    Outcome outcome();

    /** True when the game ends in this position. */
    default boolean isOver() {
        return outcome() != Outcome.ONGOING;
    }

    /** Placement and side to move: equal for positions that count as a repetition. */
    long repetitionKey();

    /**
     * Everything the search and the evaluation read from the position: equal keys mean the same
     * score, short of a hash collision.
     */
    long searchKey();

    /** The legal moves ({@link Move}), in the order of {@link #children()}. */
    default int[] legalMoves() {
        List<? extends Board> children = children();
        int[] moves = new int[children.size()];
        for (int i = 0; i < moves.length; i++) {
            moves[i] = children.get(i).lastMove();
        }
        return moves;
    }

    /**
     * The position after {@code move}, or {@code null} when it is not legal. A promotion with no
     * piece named promotes to the first piece the board offers (chess: the queen).
     */
    default Board play(int move) {
        Board fallback = null;
        for (Board child : children()) {
            int m = child.lastMove();
            if (m == move) {
                return child;
            }
            if (Move.promotion(move) == 0 && fallback == null
                    && Move.from(m) == Move.from(move) && Move.to(m) == Move.to(move)) {
                fallback = child;
            }
        }
        return fallback;
    }

    /** The position in the board's text form (chess: FEN). */
    String toFen();
}
