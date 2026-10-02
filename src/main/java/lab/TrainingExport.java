package lab;

import arena.GameRecord;
import rules.Game;
import rules.GameStatus;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes a run's games as training data for fitting the weights directly (docs/phase-5-research.md
 * §8.3): one line per position, {@code fen,result}, the result from White's side (1, 0.5 or 0).
 * The opening's moves and positions in check are left out, since neither says much about how
 * good a quiet position is.
 */
public final class TrainingExport {

    /** Plies at the start of each game that are not written. */
    public static final int SKIP_PLIES = 10;

    private TrainingExport() {}

    /** Writes every position of {@code games}; returns how many lines it wrote. */
    public static int write(List<GameRecord> games, Writer out) throws IOException {
        int lines = 0;
        out.write("fen,result\n");
        for (GameRecord record : games) {
            String result = switch (record.result()) {
                case WHITE_WINS -> "1";
                case BLACK_WINS -> "0";
                case DRAW -> "0.5";
            };
            Game game = new Game();
            for (int ply = 0; ply < record.moves().size(); ply++) {
                game.play(record.moves().get(ply));
                GameStatus status = game.status();
                if (ply + 1 >= SKIP_PLIES && status == GameStatus.IN_PROGRESS) {
                    out.write(game.fen() + "," + result + "\n");
                    lines++;
                }
            }
        }
        return lines;
    }
}
