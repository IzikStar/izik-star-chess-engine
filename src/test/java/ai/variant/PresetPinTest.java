package ai.variant;

import ai.Minimax;
import ai.board.Boards;
import ai.board.SearchBoards;
import engine.MinimaxEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.ChessMove;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The four built-in variants are presets of the rule building blocks (docs/variant-rules.md). They
 * must play exactly as they did when each was a single goal: the same perft counts and the same
 * engine moves at fixed depth. The numbers below were recorded with the single-goal model, before the
 * building blocks; {@link #main} prints them again.
 */
class PresetPinTest {

    /** variant | fen | perft at depths 1.. | engine moves at depths 1.. */
    static final String[] PINS = {
            "chess | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 20 400 8902 197281 | d2d4 d2d4 e2e4 e2e4",
            "chess | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 48 2039 97862 | e2a6 e2a6 e2a6 e2a6",
            "chess | 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 14 191 2812 43238 | b4f4 b4f4 b4f4 b4f4",
            "chess | r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 1 | 38 1329 48378 | e1g1 e1g1 e1g1 e1g1",
            "antichess | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1 | 20 400 8067 153299 | b1a3 b1a3 b1a3 b1a3",
            "antichess | rnbqkbnr/p1pppppp/8/1p6/8/4P3/PPPP1PPP/RNBQKBNR w - - 0 2 | 1 20 24 74 | f1b5 f1b5 f1b5 f1b5",
            "antichess | 8/8/3k4/8/8/2n5/8/R6K w - - 0 1 | 16 196 2233 26861 | h1g2 h1g2 h1g2 h1g2",
            "king-of-the-hill | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 20 400 8902 197281 | d2d4 d2d4 e2e4 e2e4",
            "king-of-the-hill | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 48 2039 97862 | e2a6 e2a6 e2a6 e2a6",
            "king-of-the-hill | 7k/8/8/8/8/3K4/8/8 w - - 0 1 | 8 18 144 774 | d3d4 d3d4 d3d4 d3d4",
            "king-of-the-hill | 8/8/4k3/8/2K5/8/8/7q w - - 0 1 | 7 172 932 24640 | c4d4 c4d4 c4d4 c4d4",
            "three-check | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 3+3 0 1 | 20 400 8902 197281 | d2d4 d2d4 e2e4 e2e4",
            "three-check | r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 3+3 2 3 | 42 1232 49147 | f3f7 f3f7 f3f7 f3f7",
            "three-check | 4k3/8/8/8/8/8/3Q4/4K3 w - - 1+3 0 1 | 26 78 2113 7987 | d2d8 d2d8 d2d8 d2d8",
    };

    static final String[][] POSITIONS = {
            {"chess", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "4"},
            {"chess", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", "3"},
            {"chess", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", "4"},
            {"chess", "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 1", "3"},
            {"antichess", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", "4"},
            {"antichess", "rnbqkbnr/p1pppppp/8/1p6/8/4P3/PPPP1PPP/RNBQKBNR w - - 0 2", "4"},
            {"antichess", "8/8/3k4/8/8/2n5/8/R6K w - - 0 1", "4"},
            {"king-of-the-hill", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "4"},
            {"king-of-the-hill", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", "3"},
            {"king-of-the-hill", "7k/8/8/8/8/3K4/8/8 w - - 0 1", "4"},
            {"king-of-the-hill", "8/8/4k3/8/2K5/8/8/7q w - - 0 1", "4"},
            {"three-check", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 3+3 0 1", "4"},
            {"three-check", "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 3+3 2 3", "3"},
            {"three-check", "4k3/8/8/8/8/8/3Q4/4K3 w - - 1+3 0 1", "4"},
    };

    record Pin(Variant variant, String fen, List<Long> perft, List<String> moves) {
        @Override
        public String toString() {
            return variant.id() + " " + fen;
        }
    }

    static List<Pin> pins() {
        List<Pin> out = new ArrayList<>();
        for (String line : PINS) {
            String[] p = line.split(" \\| ");
            out.add(new Pin(Variants.byId(p[0]).orElseThrow(), p[1],
                    java.util.Arrays.stream(p[2].split(" ")).map(Long::parseLong).toList(), List.of(p[3].split(" "))));
        }
        return out;
    }

    static String move(Variant variant, String fen, int depth) {
        ChessMove m = MinimaxEngine.searchAtDepth(variant, List.of(fen), depth, Minimax.Options.DEFAULT, () -> false);
        return m == null ? "-" : m.toUci();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pins")
    @DisplayName("A built-in variant's perft counts are the recorded ones")
    void perftIsPinned(Pin pin) {
        List<Long> got = new ArrayList<>();
        for (int d = 1; d <= pin.perft().size(); d++) {
            got.add(SearchBoards.perft(Boards.fromFen(pin.variant(), pin.fen()), d));
        }
        assertEquals(pin.perft(), got);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pins")
    @DisplayName("The engine plays a built-in variant's recorded move at each depth")
    void movesArePinned(Pin pin) {
        List<String> got = new ArrayList<>();
        for (int d = 1; d <= pin.moves().size(); d++) {
            got.add(move(pin.variant(), pin.fen(), d));
        }
        assertEquals(pin.moves(), got);
    }

    /** Prints the pins as they come out now. */
    public static void main(String[] args) {
        for (String[] p : POSITIONS) {
            Variant v = Variants.byId(p[0]).orElseThrow();
            int depth = Integer.parseInt(p[2]);
            StringBuilder perft = new StringBuilder();
            StringBuilder moves = new StringBuilder();
            for (int d = 1; d <= depth; d++) {
                perft.append(d > 1 ? " " : "").append(SearchBoards.perft(Boards.fromFen(v, p[1]), d));
            }
            for (int d = 1; d <= 4; d++) {
                moves.append(d > 1 ? " " : "").append(move(v, p[1], d));
            }
            System.out.println("            \"" + p[0] + " | " + p[1] + " | " + perft + " | " + moves + "\",");
        }
    }
}
