package lab;

import ai.variant.Variant;
import ai.variant.Variants;
import arena.GameRecord;
import rules.Game;
import rules.MoveResult;
import rules.Rules;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes games as training data for fitting the weights directly (docs/phase-5-research.md §8.3)
 * or training a network (docs/phase-7-research.md): one line per quiet position,
 * {@code fen,result,score}, in any variant.
 * <ul>
 *   <li>{@code fen}: the position before a move, as the variant writes it;</li>
 *   <li>{@code result}: how the game ended, from White's side: 1, 0.5 or 0;</li>
 *   <li>{@code score}: the mover's search score for the position in centipawns from White's side
 *       ({@link GameRecord#scores()}), empty when the player gave none.</li>
 * </ul>
 * A position is kept when it comes after the first plies (the opening says little about how good
 * a quiet position is), the side to move is not in check, and the move it played was neither a
 * capture nor a promotion: a search would resolve those first, so a static evaluation cannot be
 * expected to match the result there. {@code lab.NetData} turns these lines into the network's inputs.
 */
public final class TrainingExport {

    /** Plies at the start of each game that are not written. */
    public static final int SKIP_PLIES = 10;

    /** The first line of every file written here. */
    public static final String HEADER = "fen,result,score";

    private TrainingExport() {}

    /** Writes the quiet positions of {@code games} of chess; returns how many lines it wrote. */
    public static int write(List<GameRecord> games, Writer out) throws IOException {
        return write(Variants.CHESS, games, out);
    }

    /**
     * Writes the quiet positions of {@code games}, replayed by the rules of {@code variant} (the FEN
     * is that game's: an antichess position is written as antichess sees it), skipping the first
     * {@link #SKIP_PLIES}; returns the lines written.
     */
    public static int write(Variant variant, List<GameRecord> games, Writer out) throws IOException {
        return write(variant, games, SKIP_PLIES, out);
    }

    /** As {@link #write(Variant, List, Writer)}, skipping the first {@code skipPlies} of each game. */
    public static int write(Variant variant, List<GameRecord> games, int skipPlies, Writer out) throws IOException {
        int lines = 0;
        out.write(HEADER + "\n");
        for (GameRecord record : games) {
            String result = result(record);
            Game game = new Game(variant);
            for (int ply = 0; ply < record.moves().size(); ply++) {
                MoveResult m = game.play(record.moves().get(ply));
                if (ply >= skipPlies && !m.isCapture() && !m.isPromotion() && !Rules.isCheck(variant, m.fenBefore())) {
                    int score = record.score(ply);
                    out.write(m.fenBefore() + "," + result + "," + (score == GameRecord.NO_SCORE ? "" : score) + "\n");
                    lines++;
                }
            }
        }
        return lines;
    }

    /** {@code record}'s result from White's side as the file writes it: 1, 0.5 or 0. */
    public static String result(GameRecord record) {
        return switch (record.result()) {
            case WHITE_WINS -> "1";
            case BLACK_WINS -> "0";
            case DRAW -> "0.5";
        };
    }
}
