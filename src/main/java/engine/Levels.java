package engine;

/**
 * The difficulty ladder: who plays each level, and how strong it measured
 * (docs/difficulty-ladder.md). Levels are the numbers the player picks, 0-13.
 *
 * <ul>
 *   <li>0: random legal moves, below the ladder.</li>
 *   <li>1-2: the built-in engine at depth 1, a quarter (1) or an eighth (2) of its moves random.</li>
 *   <li>3-8: the built-in engine at depth 1, 2, 3, 4, 6, 7 ({@link MinimaxEngine#searchDepth}).
 *       Depth 5 is left out: it measured only about 120 Elo above depth 4 and 100 below depth 6.
 *       Depth 7 is the deepest that fits the 5 s cap in the middlegame; depth 8 took about 9 s
 *       and, held to the cap, was no stronger.</li>
 *   <li>9-12: Stockfish held to a UCI_Elo, half a second a move.</li>
 *   <li>13: Stockfish at full strength, a second a move.</li>
 * </ul>
 * Hints ({@link #HINT}) are the strongest the app has: Stockfish at full strength for
 * {@link #HINT_MOVE_TIME_MS}, or without Stockfish the built-in engine's top level.
 *
 * The steps between levels are 150-350 Elo as measured by {@code arena.LadderCalibration}, on
 * Stockfish's UCI_Elo scale (a computer rating list, not a human federation's).
 */
public final class Levels {

    public static final int RANDOM = 0;
    public static final int MAX = 13;
    /** The built-in engine's strongest level. */
    public static final int TOP_BUILT_IN = 8;
    /** Levels from here up are Stockfish. */
    public static final int STOCKFISH_FROM = TOP_BUILT_IN + 1;
    /** Hints: Stockfish at full strength. Not a level the player can pick. */
    public static final int HINT = MAX + 1;
    /** How long Stockfish thinks about a hint. */
    public static final long HINT_MOVE_TIME_MS = 4000;

    /** The built-in engine's nominal search depth at Levels 1-8. */
    private static final int[] DEPTH = {1, 1, 1, 2, 3, 4, 6, 7};
    /** The share of its moves Levels 1-8 play at random, in percent. */
    private static final int[] RANDOM_PERCENT = {25, 12, 0, 0, 0, 0, 0, 0};

    /** Stockfish's UCI_Elo for Levels 9-12. */
    private static final int[] STOCKFISH_ELO = {2150, 2400, 2650, 2900};

    private Levels() {}

    /** {@code level} clamped to the ladder, 0 to {@link #MAX}. */
    public static int clamp(int level) {
        return Math.max(RANDOM, Math.min(MAX, level));
    }

    public static boolean isStockfish(int level) {
        return level >= STOCKFISH_FROM;
    }

    /** The built-in engine's nominal search depth at a built-in level (1-8); 0 at Level 0. */
    public static int builtInDepth(int level) {
        if (level <= RANDOM) {
            return 0;
        }
        return DEPTH[Math.min(TOP_BUILT_IN, level) - 1];
    }

    /** The share of random moves at a built-in level, in percent. */
    public static int randomPercent(int level) {
        if (level <= RANDOM) {
            return 100;
        }
        return RANDOM_PERCENT[Math.min(TOP_BUILT_IN, level) - 1];
    }

    /** Stockfish's UCI_Elo at {@code level}, or 0 for full strength (Level 13 and hints). */
    public static int stockfishElo(int level) {
        int i = level - STOCKFISH_FROM;
        return i >= 0 && i < STOCKFISH_ELO.length ? STOCKFISH_ELO[i] : 0;
    }

    /** How long Stockfish thinks at {@code level}: 0.5 s while held to an Elo, 1 s at Level 13, 4 s for a hint. */
    public static long stockfishMoveTimeMs(int level) {
        if (level >= HINT) {
            return HINT_MOVE_TIME_MS;
        }
        return stockfishElo(level) > 0 ? 500 : 1000;
    }
}
