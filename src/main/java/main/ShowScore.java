package main;

import game.GameConfig;
import rules.Position;

/** The material balance shown in the score panel (P 1, N/B 3, R 5, Q 9). */
final class ShowScore {

    private ShowScore() {}

    static int material(Position position, boolean white) {
        int score = 0;
        for (char piece : position.pieces()) {
            if (Character.isUpperCase(piece) != white) {
                continue;
            }
            score += switch (Character.toLowerCase(piece)) {
                case 'p' -> 1;
                case 'n', 'b' -> 3;
                case 'r' -> 5;
                case 'q' -> 9;
                default -> 0;
            };
        }
        return score;
    }

    static void update(Position position, GameConfig config) {
        // the side that is ahead shows its lead; the other side's label is blanked (negative)
        int diff = material(position, true) - material(position, false);
        Main.updateScores(diff, -diff, config.humanPlaysWhite());
    }
}
