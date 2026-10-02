package ai.eval;

import ai.BitBoard.BitBoard;

/**
 * Scores a position for the search. Built from a {@link ParamVector}, so the same search can play
 * with any set of weights: today's hand-written evaluation is one implementation, a neural network
 * reading the board can be another (docs/phase-5-research.md §8.4). Implementations hold no
 * mutable state, so one evaluator may serve several searches on several threads at once.
 */
public interface Evaluator {

    /**
     * The score of {@code board} for the side choosing the move at the search root
     * ({@code rootIsBlack}); higher is better for that side. Mates score beyond
     * {@code ±BitBoardEvaluate.MATE}.
     */
    int evaluate(BitBoard board, boolean rootIsBlack);

    /** The weights this evaluator plays with. */
    ParamVector params();
}
