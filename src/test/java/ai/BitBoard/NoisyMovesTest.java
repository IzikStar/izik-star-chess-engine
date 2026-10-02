package ai.BitBoard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BitBoard#getNoisyNextStates()}, the moves the quiescence search plays, is built apart from
 * the full move list. Everywhere in the perft trees it must be exactly the legal captures and
 * queen promotions, less the captures of a defended piece by a more valuable one.
 */
class NoisyMovesTest {

    private static final long NODES = 20_000;
    private static final int[] VALUE = {0, 1000, 9, 5, 3, 3, 1}; // king 1 … pawn 6

    private static int square(String uci, int at) {
        return (8 - (uci.charAt(at + 1) - '0')) * 8 + (uci.charAt(at) - 'a');
    }

    private static int pieceOn(BitBoard b, int sq) {
        long bit = 1L << sq;
        long[][] byType = {{}, {b.whiteKings, b.blackKings}, {b.whiteQueens, b.blackQueens},
                {b.whiteRooks, b.blackRooks}, {b.whiteBishops, b.blackBishops},
                {b.whiteKnights, b.blackKnights}, {b.whitePawns, b.blackPawns}};
        for (int type = 1; type <= 6; type++) {
            if (((byType[type][0] | byType[type][1]) & bit) != 0) return type;
        }
        return 0;
    }

    /** What the quiescence search should play, worked out from the full move list. */
    private static Set<String> expected(BitBoard board) {
        Set<String> moves = new HashSet<>();
        int opponent = board.isWhiteToMove ? 0 : 1;
        for (BitBoard child : board.getNextStates()) {
            String uci = SearchBoards.uci(board, child);
            if (!child.isNoisyChildOf(board) || uci.length() == 5 && uci.charAt(4) != 'q') continue;
            int from = square(uci, 0);
            int to = square(uci, 2);
            int victim = pieceOn(board, to);
            boolean losing = victim != 0 && VALUE[pieceOn(board, from)] > VALUE[victim]
                    && Attacks.attacked(board, to, opponent);
            if (!losing) moves.add(uci);
        }
        return moves;
    }

    private static Set<String> noisy(BitBoard board) {
        Set<String> moves = new HashSet<>();
        for (BitBoard child : board.getNoisyNextStates()) {
            moves.add(SearchBoards.uci(board, child));
        }
        return moves;
    }

    private static void walk(BitBoard board, int depth, String path) {
        assertEquals(expected(board), noisy(board), "after: " + path);
        if (depth > 0) {
            for (BitBoard child : board.getNextStates()) {
                walk(child, depth - 1, path + " " + SearchBoards.uci(board, child));
                child.nextStates = null;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules.PerftPositions#all")
    @DisplayName("The noisy moves are the legal captures and queen promotions that do not lose material")
    void agreesWithTheMoveList(Position p) {
        walk(BitBoardRules.fromFen(p.fen()), p.depthWithin(NODES), p.fen() + " moves");
    }

    @Test
    @DisplayName("Victims come most valuable first, and among equal victims the cheapest attacker first")
    void ordersVictimFirstThenCheapestAttacker() {
        // White can take the d5 queen with the pawn or the knight, and the undefended c7 rook with the queen
        BitBoard board = BitBoardRules.fromFen("k7/2r5/8/3q4/4P3/4N3/8/K1Q5 w - - 0 1");
        List<String> order = board.getNoisyNextStates().stream().map(c -> SearchBoards.uci(board, c)).toList();
        assertEquals(List.of("e4d5", "e3d5", "c1c7"), order);
    }

    @Test
    @DisplayName("A queen does not take a defended pawn")
    void skipsLosingCaptures() {
        BitBoard board = BitBoardRules.fromFen("k7/8/2p5/3p4/8/8/8/K2Q4 w - - 0 1");
        assertTrue(board.getNoisyNextStates().isEmpty());
    }
}
