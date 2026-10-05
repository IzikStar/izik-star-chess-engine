package arena;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A short opening line in UCI that a game of the arena starts from. */
public record Opening(String name, List<String> moves) {

    public Opening {
        moves = List.copyOf(moves);
    }

    /**
     * {@code count} openings of {@code variant} made of {@code plies} random legal moves each, named
     * "random 1", "random 2"...; a line that ends the game is drawn again. The same seed gives the same
     * openings. Variants have no opening book, so their games start like this.
     */
    public static List<Opening> random(ai.variant.Variant variant, int count, int plies, long seed) {
        java.util.Random random = new java.util.Random(seed);
        List<Opening> openings = new ArrayList<>();
        java.util.Set<List<String>> seen = new java.util.HashSet<>();
        int tries = 0;
        while (openings.size() < count && tries++ < count * 50) {
            rules.Game game = new rules.Game(variant);
            List<String> moves = new ArrayList<>();
            for (int i = 0; i < plies && !game.status().isGameOver(); i++) {
                List<rules.ChessMove> legal = game.legalMoves();
                rules.ChessMove move = legal.get(random.nextInt(legal.size()));
                game.play(move);
                moves.add(move.toUci());
            }
            if (!game.status().isGameOver() && seen.add(moves)) {
                openings.add(new Opening("random " + (openings.size() + 1), moves));
            }
        }
        return List.copyOf(openings);
    }

    /** The arena's suite of about fifty balanced openings ({@code arena/openings.txt}). */
    public static List<Opening> suite() {
        try (InputStream in = Opening.class.getResourceAsStream("/arena/openings.txt")) {
            if (in == null) {
                throw new IllegalStateException("arena/openings.txt is missing");
            }
            List<Opening> openings = new ArrayList<>();
            for (String line : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|");
                openings.add(new Opening(parts[0].trim(), Arrays.asList(parts[1].trim().split("\\s+"))));
            }
            return List.copyOf(openings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
