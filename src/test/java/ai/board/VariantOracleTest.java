package ai.board;

import ai.variant.TestVariants;
import ai.variant.Variant;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Phase 6 R4: every built-in variant plays exactly Fairy-Stockfish's moves. {@code oracle.txt}
 * (made by {@code tools/variant_oracle.py}) holds seeded random games with Fairy-Stockfish's legal
 * moves at every ply, game ends included, and perft counts from a few positions of each variant.
 *
 * <p>R5: the same for the made-up variants in {@code variants/made/*.json}, whose invented pieces
 * (Amazon, Archbishop, Knightrider, forward-only pieces) reached Fairy-Stockfish as the Betza text in
 * those files.
 */
class VariantOracleTest {

    record Line(String kind, Variant variant, String fen, String rest) {
        @Override
        public String toString() {
            return variant.id() + " " + fen;
        }
    }

    private static List<Line> lines(String kind) {
        List<Line> out = new ArrayList<>();
        try (InputStream in = VariantOracleTest.class.getResourceAsStream("/variants/oracle.txt")) {
            if (in == null) {
                throw new IllegalStateException("variants/oracle.txt is missing");
            }
            for (String line : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList()) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(" \\| ", -1);
                if (parts[0].equals(kind)) {
                    Variant variant = Variants.byId(parts[1]).orElseGet(() -> TestVariants.made(parts[1]));
                    out.add(new Line(kind, variant, parts[2], String.join(" | ", Arrays.asList(parts).subList(3, parts.length))));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    static List<Line> moves() {
        return lines("moves");
    }

    static List<Line> perft() {
        return lines("perft");
    }

    private static GenericBoard board(Line line) {
        return GenericBoard.fromFen(BoardRules.of(line.variant()), line.fen());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("moves")
    @DisplayName("Along random games the legal moves equal Fairy-Stockfish's, and a game with none is over")
    void sameMovesAsFairyStockfish(Line line) {
        GenericBoard board = board(line);
        String ours = board.children().stream().map(SearchBoards::uci).sorted().collect(Collectors.joining(" "));
        assertEquals(line.rest(), ours, line.toString());
        if (ours.isEmpty()) {
            assertNotEquals(Outcome.ONGOING, board.outcome(), line.toString());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("perft")
    @DisplayName("Perft counts equal Fairy-Stockfish's")
    void perftEqualsFairyStockfish(Line line) {
        String[] depthAndCount = line.rest().split(" \\| ");
        int depth = Integer.parseInt(depthAndCount[0]);
        assertEquals(Long.parseLong(depthAndCount[1]), SearchBoards.perft(board(line), depth), line + " depth " + depth);
    }
}
