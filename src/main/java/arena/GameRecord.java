package arena;

import java.util.List;

/**
 * A finished arena game.
 *
 * @param moves  every move in UCI, the opening's included
 * @param reason why it ended: a {@link rules.GameStatus} name, or {@code PLY_CAP} when the arena
 *               stopped it as a draw
 * @param seed   repeats the game exactly with the same players
 */
public record GameRecord(String white, String black, String opening, List<String> moves, Result result,
                         String reason, long seed) {

    public enum Result {
        WHITE_WINS, BLACK_WINS, DRAW;

        /** The points {@code white} (true) or Black scored: 1, ½ or 0. */
        public double score(boolean white) {
            return switch (this) {
                case DRAW -> 0.5;
                case WHITE_WINS -> white ? 1 : 0;
                case BLACK_WINS -> white ? 0 : 1;
            };
        }
    }

    public GameRecord {
        moves = List.copyOf(moves);
    }

    public int plies() {
        return moves.size();
    }

    /** The points {@code player} scored in this game, or NaN if they did not play it. */
    public double scoreOf(String player) {
        if (player.equals(white)) {
            return result.score(true);
        }
        if (player.equals(black)) {
            return result.score(false);
        }
        return Double.NaN;
    }
}
