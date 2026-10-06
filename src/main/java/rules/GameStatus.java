package rules;

/**
 * The single answer to "what is the state of this game" — the value Phase 2 collapses three
 * independent code paths ({@code CheckScanner}, the old bitboard's {@code getStatus}, and the
 * simulate-and-revert in {@code Move.getStatusString}) down into.
 */
public enum GameStatus {

    /** Game continues, side to move is not in check. */
    IN_PROGRESS,
    /** Game continues, side to move is in check. */
    CHECK,
    /** Side to move has no legal move and is in check. */
    CHECKMATE,
    /** Side to move has no legal move and is not in check. */
    STALEMATE,
    /** 50 full moves (100 plies) without a capture or pawn move. */
    DRAW_FIFTY_MOVE,
    /** The same position has occurred three times. */
    DRAW_THREEFOLD,
    /** Neither side has enough material to force mate. */
    DRAW_INSUFFICIENT_MATERIAL,
    /** A squares goal (King of the Hill): the opponent's piece reached a goal square; the side to move lost. */
    HILL_REACHED,
    /** Three-check (or N-check): the opponent gave the last check; the side to move lost. */
    CHECKS_GIVEN,
    /** Antichess: the side to move has no pieces left, and wins. */
    NO_PIECES_LEFT,
    /** Antichess, or stalemate counted as a win: the side to move has no legal move, and wins. */
    NO_MOVES_LEFT,
    /** A capture-all goal: the side to move has no piece of the named types left, and lost. */
    ALL_CAPTURED,
    /** The bare-royal goal: the side to move has nothing but royal pieces left, and lost. */
    BARE_ROYAL,
    /** Royal mode LAST_STANDING: the side to move has no royal piece left, and lost. */
    ROYALS_LOST,
    /** Stalemate counted as a loss: the side to move has no legal move (and is not checkmated), and lost. */
    STALEMATE_LOSS;

    public boolean isGameOver() {
        return this != IN_PROGRESS && this != CHECK;
    }

    /** The player to move has lost: mated, or the opponent reached the variant's goal. */
    public boolean sideToMoveLost() {
        return this == CHECKMATE || this == HILL_REACHED || this == CHECKS_GIVEN || this == ALL_CAPTURED
                || this == BARE_ROYAL || this == ROYALS_LOST || this == STALEMATE_LOSS;
    }

    /** The player to move has won (antichess, or stalemate counted as a win). */
    public boolean sideToMoveWon() {
        return this == NO_PIECES_LEFT || this == NO_MOVES_LEFT;
    }

    /** "1-0", "0-1", "1/2-1/2", or null while the game goes on. */
    public String result(boolean whiteToMove) {
        if (sideToMoveLost() || sideToMoveWon()) {
            return whiteToMove == sideToMoveWon() ? "1-0" : "0-1";
        }
        return isDraw() ? "1/2-1/2" : null;
    }

    public boolean isDraw() {
        return this == STALEMATE || this == DRAW_FIFTY_MOVE
                || this == DRAW_THREEFOLD || this == DRAW_INSUFFICIENT_MATERIAL;
    }
}
