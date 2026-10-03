package game;

/**
 * A game that ended off the board: a resignation, a flag fall, or a draw agreed (the board's own
 * endings, mate and the automatic draws, are a {@link rules.GameStatus}).
 *
 * @param reason how it ended
 * @param white  the side that resigned or ran out of time; meaningless for {@link Reason#AGREEMENT}
 * @param result {@code "1-0"}, {@code "0-1"} or {@code "1/2-1/2"}
 */
public record GameEnd(Reason reason, boolean white, String result) {

    public enum Reason {
        RESIGNATION,
        /** Ran out of time, and the opponent has the material to mate. */
        TIMEOUT,
        /** Ran out of time, but the opponent could never mate: a draw. */
        TIMEOUT_VS_INSUFFICIENT_MATERIAL,
        AGREEMENT
    }

    public static GameEnd resigned(boolean white) {
        return new GameEnd(Reason.RESIGNATION, white, white ? "0-1" : "1-0");
    }

    public static GameEnd flagged(boolean white, boolean opponentCanMate) {
        return opponentCanMate
                ? new GameEnd(Reason.TIMEOUT, white, white ? "0-1" : "1-0")
                : new GameEnd(Reason.TIMEOUT_VS_INSUFFICIENT_MATERIAL, white, "1/2-1/2");
    }

    public static GameEnd agreed() {
        return new GameEnd(Reason.AGREEMENT, false, "1/2-1/2");
    }
}
