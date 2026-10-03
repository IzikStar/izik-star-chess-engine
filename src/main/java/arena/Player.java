package arena;

import ai.BitBoard.BitBoardEvaluate;
import ai.eval.Evaluator;

/**
 * One side of an arena game: an evaluation and how the search uses it.
 *
 * @param name       how results name this player
 * @param evaluator  the evaluation, e.g. a {@link BitBoardEvaluate} built from a parameter vector
 * @param depth      fixed search depth (fixed depth keeps results independent of the machine's load)
 * @param variety    how far below the best move (pawn = 100) a move may score and still be played;
 *                   the arena seeds it, so a game is varied yet repeatable
 * @param quiescence false plays the search as it was before Phase 5, to compare against
 * @param speedups   false searches without the Phase 5b transposition table and move ordering, to
 *                   compare against
 * @param moveMillis 0 searches exactly {@code depth}; more gives each move that long, deepening up to
 *                   {@code depth} (games are then no longer repeatable: they depend on the machine)
 */
public record Player(String name, Evaluator evaluator, int depth, int variety, boolean quiescence,
                     boolean speedups, long moveMillis) {

    public Player {
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be at least 1");
        }
        if (variety < 0) {
            throw new IllegalArgumentException("variety must not be negative");
        }
        if (moveMillis < 0) {
            throw new IllegalArgumentException("moveMillis must not be negative");
        }
    }

    /** A fixed-depth player with the Phase 5b search. */
    public Player(String name, Evaluator evaluator, int depth, int variety, boolean quiescence) {
        this(name, evaluator, depth, variety, quiescence, true, 0);
    }

    /** A player with the usual search: quiescence on, the given variety. */
    public static Player of(String name, Evaluator evaluator, int depth, int variety) {
        return new Player(name, evaluator, depth, variety, true);
    }

    /** The default weights at {@code depth}: the yardstick candidates are measured against. */
    public static Player yardstick(int depth, int variety) {
        return of("default", BitBoardEvaluate.DEFAULT, depth, variety);
    }
}
