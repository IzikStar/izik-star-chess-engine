package engine;

import rules.ChessMove;

import java.util.List;

/**
 * What an engine is asked: the position to move in, the game that led to it (start position +
 * moves, so an engine that keeps a session, like Stockfish, can follow the game and see
 * repetitions), the strength, a cancellation flag (Phase 4), and the most the move may take on
 * the game's clock ({@link TimeBudget}).
 *
 * @param fen        the position to move in: {@code startFen} after {@code moves}
 * @param startFen   the game's starting position
 * @param moves      the moves played since {@code startFen}
 * @param skillLevel the level, 0-13 ({@link Levels}; hints use {@link Levels#HINT})
 * @param cancel     set when the answer is no longer wanted
 * @param timeBudgetMs the most this move may take on the clock, or {@link TimeBudget#NONE}: the
 *                   engine thinks as its level says, but no longer than this
 */
public record SearchRequest(String fen, String startFen, List<ChessMove> moves, int skillLevel,
                            Cancellation cancel, long timeBudgetMs) {

    public SearchRequest {
        moves = List.copyOf(moves);
    }

    /** A request with no limit from a clock. */
    public SearchRequest(String fen, String startFen, List<ChessMove> moves, int skillLevel, Cancellation cancel) {
        this(fen, startFen, moves, skillLevel, cancel, TimeBudget.NONE);
    }

    /** A request with no game history behind it. */
    public static SearchRequest of(String fen, int skillLevel) {
        return new SearchRequest(fen, fen, List.of(), skillLevel, Cancellation.NONE);
    }

    public SearchRequest withSkillLevel(int level) {
        return new SearchRequest(fen, startFen, moves, level, cancel, timeBudgetMs);
    }

    public SearchRequest withTimeBudgetMs(long budgetMs) {
        return new SearchRequest(fen, startFen, moves, skillLevel, cancel, budgetMs);
    }
}
