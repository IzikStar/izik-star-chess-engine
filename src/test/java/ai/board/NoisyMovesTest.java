package ai.board;

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
 * {@link Board#noisyChildren()}, the moves the quiescence search plays, is built apart from the
 * full move list. Everywhere in the perft trees it must be exactly the legal captures and queen
 * promotions, less the captures of a defended piece by a more valuable one.
 */
class NoisyMovesTest {

    private static final long NODES = 20_000;
    /** By {@link ChessPosition} type: king, queen, rook, bishop, knight, pawn. */
    private static final int[] VALUE = {1000, 9, 5, 3, 3, 1};

    private static int pieceOn(ChessPosition b, int sq) {
        for (int type = ChessPosition.KING; type <= ChessPosition.PAWN; type++) {
            if (((b.pieces(0, type) | b.pieces(1, type)) & 1L << sq) != 0) {
                return type;
            }
        }
        return -1;
    }

    /** What the quiescence search should play, worked out from the full move list. */
    private static Set<String> expected(ChessPosition board) {
        Set<String> moves = new HashSet<>();
        int opponent = 1 - board.sideToMove();
        for (Board child : board.children()) {
            String uci = SearchBoards.uci(child);
            boolean capture = Long.bitCount(((ChessPosition) child).occupied(opponent))
                    < Long.bitCount(board.occupied(opponent));
            boolean promotion = uci.length() == 5;
            if (!(capture || promotion) || promotion && uci.charAt(4) != 'q') {
                continue;
            }
            int to = Move.to(child.lastMove());
            int victim = pieceOn(board, to);
            boolean losing = victim >= 0 && VALUE[pieceOn(board, Move.from(child.lastMove()))] > VALUE[victim]
                    && (board.attackedBy(opponent) & 1L << to) != 0;
            if (!losing) {
                moves.add(uci);
            }
        }
        return moves;
    }

    private static Set<String> noisy(Board board) {
        Set<String> moves = new HashSet<>();
        for (Board child : board.noisyChildren()) {
            moves.add(SearchBoards.uci(child));
        }
        return moves;
    }

    private static void walk(ChessPosition board, int depth, String path) {
        assertEquals(expected(board), noisy(board), "after: " + path);
        if (depth > 0) {
            for (Board child : board.children()) {
                walk((ChessPosition) child, depth - 1, path + " " + SearchBoards.uci(child));
            }
            board.releaseChildren();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules.PerftPositions#all")
    @DisplayName("The noisy moves are the legal captures and queen promotions that do not lose material")
    void agreesWithTheMoveList(Position p) {
        walk((ChessPosition) Boards.fromFen(p.fen()), p.depthWithin(NODES), p.fen() + " moves");
    }

    @Test
    @DisplayName("Victims come most valuable first, and among equal victims the cheapest attacker first")
    void ordersVictimFirstThenCheapestAttacker() {
        // White can take the d5 queen with the pawn or the knight, and the undefended c7 rook with the queen
        Board board = Boards.fromFen("k7/2r5/8/3q4/4P3/4N3/8/K1Q5 w - - 0 1");
        List<String> order = board.noisyChildren().stream().map(SearchBoards::uci).toList();
        assertEquals(List.of("e4d5", "e3d5", "c1c7"), order);
    }

    @Test
    @DisplayName("A queen does not take a defended pawn")
    void skipsLosingCaptures() {
        assertTrue(Boards.fromFen("k7/8/2p5/3p4/8/8/8/K2Q4 w - - 0 1").noisyChildren().isEmpty());
    }
}
