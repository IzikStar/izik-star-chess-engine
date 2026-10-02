package rules;

import java.util.List;
import java.util.stream.Stream;

/**
 * Perft counts: how many move sequences of each length start from a position. A count that
 * differs from the reference means the move generator allows an illegal move or misses a legal
 * one somewhere in the tree (docs/phase-4b-research.md §2).
 *
 * <p>The standard positions and their counts are from chessprogramming.org ("Perft Results").
 * Fourteen of the edge positions (en-passant pins, castling, promotion, mate and stalemate
 * corner cases) are the TalkChess perft suite listed on the same page; the two pawn-check
 * positions are our own. Every edge count is from Stockfish 16 ({@code go perft}).
 */
public final class PerftPositions {

    private PerftPositions() {}

    /** A position and its counts: {@code counts[d - 1]} is the count at depth {@code d}. */
    public record Position(String name, String fen, long... counts) {

        public long count(int depth) {
            return counts[depth - 1];
        }

        public int maxDepth() {
            return counts.length;
        }

        /** The deepest depth whose count is at most {@code maxNodes}, so a test can bound its cost. */
        public int depthWithin(long maxNodes) {
            int depth = 1;
            while (depth < counts.length && counts[depth] <= maxNodes) {
                depth++;
            }
            return depth;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final List<Position> STANDARD = List.of(
            new Position("start", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    20, 400, 8902, 197281, 4865609),
            new Position("kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    48, 2039, 97862, 4085603),
            new Position("position 3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                    14, 191, 2812, 43238, 674624),
            new Position("position 4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    6, 264, 9467, 422333),
            new Position("position 4 mirrored", "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
                    6, 264, 9467, 422333),
            new Position("position 5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                    44, 1486, 62379, 2103487),
            new Position("position 6", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
                    46, 2079, 89890, 3894594));

    public static final List<Position> EDGE = List.of(
            new Position("illegal en passant 1", "3k4/3p4/8/K1P4r/8/8/8/8 b - - 0 1",
                    18, 92, 1670, 10138, 185429, 1134888),
            new Position("illegal en passant 2", "8/8/4k3/8/2p5/8/B2P2K1/8 w - - 0 1",
                    13, 102, 1266, 10276, 135655, 1015133),
            new Position("en passant gives check", "8/8/1k6/2b5/2pP4/8/5K2/8 b - d3 0 1",
                    15, 126, 1928, 13931, 206379, 1440467),
            new Position("short castling gives check", "5k2/8/8/8/8/8/8/4K2R w K - 0 1",
                    15, 66, 1198, 6399, 120330, 661072),
            new Position("long castling gives check", "3k4/8/8/8/8/8/8/R3K3 w Q - 0 1",
                    16, 71, 1286, 7418, 141077, 803711),
            new Position("castling rights", "r3k2r/1b4bq/8/8/8/8/7B/R3K2R w KQkq - 0 1",
                    26, 1141, 27826, 1274206),
            new Position("castling prevented", "r3k2r/8/3Q4/8/8/5q2/8/R3K2R b KQkq - 0 1",
                    44, 1494, 50509, 1720476),
            new Position("promote out of check", "2K2r2/4P3/8/8/8/8/8/3k4 w - - 0 1",
                    11, 133, 1442, 19174, 266199, 3821001),
            new Position("discovered check", "8/8/1P2K3/8/2n5/1q6/8/5k2 b - - 0 1",
                    29, 165, 5160, 31961, 1004658),
            new Position("promote to give check", "4k3/1P6/8/8/8/8/K7/8 w - - 0 1",
                    9, 40, 472, 2661, 38983, 217342),
            new Position("underpromote to check", "8/P1k5/K7/8/8/8/8/8 w - - 0 1",
                    6, 27, 273, 1329, 18135, 92683),
            new Position("self stalemate", "K1k5/8/P7/8/8/8/8/8 w - - 0 1",
                    2, 6, 13, 63, 382, 2217),
            new Position("stalemate and checkmate", "8/k1P5/8/1K6/8/8/8/8 w - - 0 1",
                    10, 25, 268, 926, 10857, 43261, 567584),
            new Position("stalemate and checkmate 2", "8/8/2k5/5q2/5n2/8/5K2/8 b - - 0 1",
                    37, 183, 6559, 23527),
            new Position("black pawn checks", "4k3/8/8/8/8/8/3p4/4K3 w - - 0 1",
                    5, 37, 199, 2093, 12567),
            new Position("white pawn checks", "4k3/3P4/8/8/8/8/8/4K3 b - - 0 1",
                    5, 37, 199, 2093, 12567));

    public static Stream<Position> all() {
        return Stream.concat(STANDARD.stream(), EDGE.stream());
    }
}
