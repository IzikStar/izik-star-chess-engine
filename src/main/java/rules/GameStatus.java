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
    DRAW_INSUFFICIENT_MATERIAL;

    public boolean isGameOver() {
        return this != IN_PROGRESS && this != CHECK;
    }

    public boolean isDraw() {
        return this == STALEMATE || this == DRAW_FIFTY_MOVE
                || this == DRAW_THREEFOLD || this == DRAW_INSUFFICIENT_MATERIAL;
    }
}
