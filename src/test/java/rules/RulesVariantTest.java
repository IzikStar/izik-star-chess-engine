package rules;

import ai.variant.Variant;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 R4b: the rules API by variant. SAN and the way games end are checked against
 * Fairy-Stockfish ({@code variants/oracle.txt}, see {@code tools/variant_oracle.py}); the rest are
 * hand-made positions for each goal.
 */
class RulesVariantTest {

    record Line(Variant variant, String fen, String[] rest) {
        @Override
        public String toString() {
            return variant.id() + " " + fen + " " + String.join(" ", rest);
        }
    }

    private static List<Line> lines(String kind) {
        List<Line> out = new ArrayList<>();
        try (InputStream in = RulesVariantTest.class.getResourceAsStream("/variants/oracle.txt")) {
            for (String line : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList()) {
                String[] parts = line.split(" \\| ", -1);
                if (parts[0].trim().equals(kind)) {
                    out.add(new Line(Variants.byId(parts[1]).orElseThrow(), parts[2],
                            java.util.Arrays.copyOfRange(parts, 3, parts.length)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    static List<Line> san() {
        return lines("san");
    }

    static List<Line> end() {
        return lines("end");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("san")
    @DisplayName("SAN equals Fairy-Stockfish's")
    void sanEqualsFairyStockfish(Line line) {
        assertEquals(line.rest()[1], San.of(line.variant(), line.fen(), ChessMove.fromUci(line.rest()[0])), line.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("end")
    @DisplayName("A finished game is won, lost or drawn as Fairy-Stockfish says")
    void endEqualsFairyStockfish(Line line) {
        GameStatus status = Rules.status(line.variant(), line.fen());
        switch (line.rest()[0]) {
            case "win" -> assertTrue(status.sideToMoveWon(), line + ": " + status);
            case "loss" -> assertTrue(status.sideToMoveLost(), line + ": " + status);
            default -> assertTrue(status.isDraw(), line + ": " + status);
        }
    }

    @Test
    @DisplayName("Antichess: captures are compulsory; no pieces or no moves wins")
    void antichess() {
        Variant v = Variants.ANTICHESS;
        // after 1.e3 b5 White must take: Bxb5 is the only move
        assertEquals(List.of("f1b5"), Rules.legalMoves(v, "rnbqkbnr/p1pppppp/8/1p6/8/4P3/PPPP1PPP/RNBQKBNR w - - 0 2")
                .stream().map(ChessMove::toUci).toList());
        assertEquals(GameStatus.NO_PIECES_LEFT, Rules.status(v, "8/8/8/8/8/8/8/k7 w - - 0 30"));
        assertEquals("1-0", GameStatus.NO_PIECES_LEFT.result(true));
        assertEquals(GameStatus.NO_MOVES_LEFT, Rules.status(v, "8/8/8/8/8/p7/P7/8 w - - 0 30"));
        // a bare king against a bare king is no dead draw here
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(v, "k7/8/8/8/8/8/8/7K w - - 0 30"));
    }

    @Test
    @DisplayName("Antichess: a pawn promotes to a queen unless told otherwise, and may become a king")
    void antichessPromotion() {
        Game game = new Game(Variants.ANTICHESS, "8/P7/8/8/8/8/8/7k w - - 0 1");
        MoveResult queen = game.play("a7a8");
        assertEquals("a8=Q", queen.san());
        game.undo();
        assertEquals("a8=K", game.play("a7a8k").san());
    }

    @Test
    @DisplayName("King of the Hill: reaching the centre wins; a lone king is not a dead draw")
    void kingOfTheHill() {
        Variant v = Variants.KING_OF_THE_HILL;
        Game game = new Game(v, "4k3/8/8/8/8/4K3/8/8 w - - 0 40");
        MoveResult result = game.play("e3e4");
        assertEquals(GameStatus.HILL_REACHED, result.status());
        assertEquals("1-0", game.status().result(false));
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("4k3/8/8/8/8/4K3/8/8 w - - 0 40"),
                "in chess the same material is a dead draw");
    }

    @Test
    @DisplayName("Three-check: checks are counted in the FEN and the third one wins")
    void threeCheck() {
        Game game = new Game(Variants.THREE_CHECK);
        for (String uci : List.of("e2e4", "f7f6", "d1h5", "g7g6", "h5g6")) {
            game.play(uci);
        }
        assertTrue(game.fen().contains(" 1+3 "), game.fen()); // two checks given, one to go
        assertFalse(game.status().isGameOver());
        game = new Game(Variants.THREE_CHECK, "4k3/8/8/8/8/8/8/R3K3 w - - 1+3 0 20");
        MoveResult last = game.play("a1a8");
        assertEquals("Ra8#", last.san(), "a check that wins is marked as mate, as Fairy-Stockfish does");
        assertEquals(GameStatus.CHECKS_GIVEN, last.status());
        assertEquals("1-0", game.status().result(false));
    }
}
