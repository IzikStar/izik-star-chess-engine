package lab;

import ai.variant.Variants;
import arena.FairyStockfish;
import arena.GameRecord;
import arena.Match;
import arena.Opening;
import arena.Players;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Game;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Training positions outside chess: a run's games and self-play, written as fen,result. */
class SelfPlayDataTest {

    @Test
    @DisplayName("An antichess game is exported by antichess rules: every quiet position of it, its result from White's side")
    void exportsVariantGames() throws IOException {
        GameRecord game = Match.play(Variants.ANTICHESS, Players.random("a", Variants.ANTICHESS),
                Players.random("b", Variants.ANTICHESS), new Opening("none", List.of()), 300, 5);
        StringWriter out = new StringWriter();
        int lines = TrainingExport.write(Variants.ANTICHESS, List.of(game), out);
        List<String> rows = out.toString().lines().toList();
        assertEquals(TrainingExport.HEADER, rows.getFirst());
        assertEquals(lines, rows.size() - 1);
        assertTrue(lines > 0);
        // the positions are the game's own (before a move), after the first plies, in order
        Game replay = new Game(Variants.ANTICHESS);
        java.util.List<String> fens = new java.util.ArrayList<>();
        game.moves().forEach(m -> fens.add(replay.play(m).fenBefore()));
        String result = switch (game.result()) {
            case WHITE_WINS -> "1";
            case BLACK_WINS -> "0";
            case DRAW -> "0.5";
        };
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split(",", -1);
            assertEquals(3, parts.length, row);
            assertTrue(fens.contains(parts[0]), parts[0]);
            assertEquals(result, parts[1]);
            assertEquals("", parts[2], "the built-in random mover reports no score");
        }
    }

    @Test
    @DisplayName("Self-play writes positions of the game it plays, from random openings, the same for the same seed")
    void selfPlay() throws IOException {
        StringWriter a = new StringWriter(), b = new StringWriter();
        int lines = SelfPlayData.play(Variants.ANTICHESS, "random", 1, 6, 4, 2, 3, a, s -> {});
        SelfPlayData.play(Variants.ANTICHESS, "random", 1, 6, 4, 2, 3, b, s -> {});
        assertTrue(lines > 0);
        assertEquals(a.toString(), b.toString());
        for (String row : a.toString().lines().skip(1).toList()) {
            String fen = row.split(",")[0];
            assertTrue(Set.of("1", "0", "0.5").contains(row.split(",")[1]));
            new Game(Variants.ANTICHESS, fen); // an antichess position (throws if the FEN is not one)
        }
    }

    @Test
    @DisplayName("Fairy-Stockfish self-play gives antichess positions (when it is installed)")
    void fairySelfPlay() throws IOException {
        assumeTrue(FairyStockfish.installed(), "Fairy-Stockfish is not installed");
        StringWriter out = new StringWriter();
        int lines = SelfPlayData.play(Variants.ANTICHESS, "fsf:500", 1, 4, 4, 2, 1, out, s -> {});
        assertTrue(lines > 20, "positions: " + lines);
    }
}
