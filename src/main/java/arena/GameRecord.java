package arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A finished arena game.
 *
 * @param moves  every move in UCI, the opening's included
 * @param reason why it ended: a {@link rules.GameStatus} name, or {@code PLY_CAP} when the arena
 *               stopped it as a draw
 * @param seed   repeats the game exactly with the same players
 * @param scores before each move, the mover's search score for the position in centipawns from
 *               White's side ({@link #NO_SCORE} when that player reported none, as the built-in
 *               engine does not); one per move, or empty when no one reported any. Training data
 *               ({@code lab.TrainingExport}) uses them; run files do not keep them.
 */
public record GameRecord(String white, String black, String opening, List<String> moves, Result result,
                         String reason, long seed, List<Integer> scores) {

    /** A move whose player reported no score. */
    public static final int NO_SCORE = Integer.MIN_VALUE;

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
        scores = scores == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(scores));
        if (!scores.isEmpty() && scores.size() != moves.size()) {
            throw new IllegalArgumentException("one score per move, or none");
        }
    }

    /** A game without scores. */
    public GameRecord(String white, String black, String opening, List<String> moves, Result result,
                      String reason, long seed) {
        this(white, black, opening, moves, result, reason, seed, List.of());
    }

    /** The score before move {@code ply} (from 0) from White's side, or {@link #NO_SCORE}. */
    public int score(int ply) {
        return scores.isEmpty() ? NO_SCORE : scores.get(ply);
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
