package engine;

/**
 * How long the engine may think about one move when the game has a clock: a slice of the time it
 * has left plus most of the increment, never so much that it could run out.
 *
 * <p>The clock only ever shortens the engine's thinking. A level thinks as it always does (its
 * depth, its {@link MinimaxEngine#TIME_CAP_MS}, Stockfish's move time), so the levels play as
 * they were measured until time gets short; then they move faster and play weaker, as a person
 * in time trouble would.
 */
public final class TimeBudget {

    /** No limit from the clock (an untimed game, or a hint). */
    public static final long NONE = 0;
    /** The time left is shared out as if this many moves were still to come. */
    static final int MOVES_TO_GO = 25;
    /** Kept back for everything but thinking (starting the search, sending the move), at most 1 s. */
    static final long MAX_RESERVE_MS = 1000;
    /** Shortest budget: even with almost no time left the engine gets this long. */
    static final long MIN_MS = 10;

    private TimeBudget() {}

    /**
     * The most the side to move should spend on this move, in ms, given the time it has left
     * and the increment it gets back after the move.
     */
    public static long forMove(long remainingMs, long incrementMs) {
        long reserve = Math.min(MAX_RESERVE_MS, remainingMs / 10);
        long usable = Math.max(0, remainingMs - reserve);
        long budget = usable / MOVES_TO_GO + incrementMs * 3 / 4;
        // with a big increment, still never bet more than half of what is left on one move
        budget = Math.min(budget, usable / 2);
        return Math.max(MIN_MS, budget);
    }

    /** {@code levelMs} held to {@code budgetMs}, unless the budget is {@link #NONE}. */
    public static long cap(long levelMs, long budgetMs) {
        return budgetMs > NONE ? Math.min(levelMs, budgetMs) : levelMs;
    }
}
