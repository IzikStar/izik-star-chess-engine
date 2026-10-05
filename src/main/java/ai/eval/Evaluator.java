package ai.eval;

import ai.board.Board;

/**
 * Scores a position for the search. Built from a {@link ParamVector}, so the same search can play
 * with any set of weights: today's hand-written evaluation is one implementation, a neural network
 * reading the board can be another (docs/phase-5-research.md §8.4). Implementations hold no
 * mutable state, so one evaluator may serve several searches on several threads at once.
 */
public interface Evaluator {

    /** A won game scores at least this (a lost one at most minus this); any other score is far below. */
    int MATE = 100_000_000;

    /**
     * The score of {@code board} for the player choosing the move at the search root
     * ({@code rootPlayer}, see {@link Board#sideToMove()}); higher is better for that player. Mates
     * score beyond {@code ±MATE}.
     */
    int evaluate(Board board, int rootPlayer);

    /** The weights this evaluator plays with. */
    ParamVector params();
}
