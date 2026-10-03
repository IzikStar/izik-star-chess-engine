package rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writing and reading PGN. */
class PgnTest {

    private static List<MoveResult> playUci(String fen, String... ucis) {
        Game g = new Game(fen);
        for (String u : ucis) {
            g.play(u);
        }
        return g.moves();
    }

    @Test
    @DisplayName("Writes tags, numbered SAN moves and the result")
    void writes() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", "Test");
        tags.put("White", "Player \"one\"");
        String pgn = Pgn.write(tags, Position.START_FEN,
                playUci(Position.START_FEN, "f2f3", "e7e5", "g2g4", "d8h4"), "0-1");
        assertEquals("""
                [Event "Test"]
                [White "Player \\"one\\""]
                [Result "0-1"]

                1. f3 e5 2. g4 Qh4# 0-1
                """, pgn);
    }

    @Test
    @DisplayName("A game from a set-up position with Black to move gets SetUp, FEN and 1...")
    void writesSetUp() {
        String fen = "4k3/8/8/8/8/8/4P3/4K3 b - - 0 1";
        String pgn = Pgn.write(Map.of(), fen, playUci(fen, "e8d7", "e2e4"), null);
        assertTrue(pgn.contains("[SetUp \"1\"]\n[FEN \"" + fen + "\"]"), pgn);
        assertTrue(pgn.endsWith("1... Kd7 2. e4 *\n"), pgn);
    }

    @Test
    @DisplayName("Long games wrap at 80 columns")
    void wraps() {
        String[] moves = new String[40];
        for (int i = 0; i < 40; i += 4) {
            moves[i] = "g1f3";
            moves[i + 1] = "g8f6";
            moves[i + 2] = "f3g1";
            moves[i + 3] = "f6g8";
        }
        Game g = new Game();
        for (String m : moves) {
            if (g.status().isGameOver()) {
                break;
            }
            g.play(m);
        }
        String pgn = Pgn.write(Map.of(), Position.START_FEN, g.moves(), "1/2-1/2");
        for (String line : pgn.split("\n")) {
            assertTrue(line.length() <= 80, line);
        }
    }

    @Test
    @DisplayName("What it writes, it reads back")
    void roundTrip() {
        List<MoveResult> moves = playUci(Position.START_FEN, "e2e4", "d7d5", "e4d5", "g8f6", "f1b5", "c7c6",
                "d5c6", "d8a5", "c6b7", "a5b5", "b7a8n");
        String pgn = Pgn.write(Map.of("Event", "R"), Position.START_FEN, moves, null);
        Pgn.Parsed parsed = Pgn.read(pgn);
        assertEquals(moves.stream().map(MoveResult::move).toList(), parsed.moves());
        assertEquals("R", parsed.tags().get("Event"));
    }

    @Test
    @DisplayName("Reads what other programs write: comments, variations, NAGs, 0-0, e8Q, check marks and UCI")
    void readsVariants() {
        Pgn.Parsed p = Pgn.read("""
                [Event "Casual"]
                [Site "?"]
                1.e4 {best by test} e5 $1 2.Nf3!? Nc6 (2...d6 3.d4) 3.Bc4 Bc5 4.0-0 ; castles
                Nf6 5.d2d3 d6 1-0
                """);
        assertEquals(10, p.moves().size());
        assertEquals("e1g1", p.moves().get(6).toUci());
        assertEquals("d2d3", p.moves().get(8).toUci());

        Pgn.Parsed promo = Pgn.read("[FEN \"8/4P1k1/8/8/8/8/8/4K3 w - - 0 1\"]\n1. e8Q Kf6 2. Qe2+ *");
        assertEquals("e7e8q", promo.moves().get(0).toUci());
        assertEquals(3, promo.moves().size());
    }

    @Test
    @DisplayName("An illegal move is named; a broken FEN is refused")
    void refuses() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Pgn.read("1. e4 e5 2. Ke3 Nc6"));
        assertEquals("move 2. Ke3 is not a legal move", e.getMessage());
        IllegalArgumentException black = assertThrows(IllegalArgumentException.class, () -> Pgn.read("1. e4 Nf3"));
        assertEquals("move 1... Nf3 is not a legal move", black.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Pgn.read("[FEN \"nonsense\"]\n1. e4"));
    }
}
