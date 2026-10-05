package ai.BitBoard;

import ai.board.Board;
import ai.board.Boards;
import ai.board.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 6 R1: the {@link Board} view of the bitboard gives the same moves and the same next
 * positions as the rules bridge it replaced ({@link BitBoardRules#legalMoves} and
 * {@link BitBoardRules#applyMove}, which read moves by diffing parent and child), along random
 * games from every perft position.
 */
class BoardInterfaceTest {

    static Stream<PerftPositions.Position> positions() {
        return Stream.concat(PerftPositions.STANDARD.stream(), PerftPositions.EDGE.stream());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("Moves and next positions match the old rules bridge along random games")
    void sameAsRulesBridge(PerftPositions.Position start) {
        Random random = new Random(6);
        for (int game = 0; game < 20; game++) {
            String fen = start.fen();
            for (int ply = 0; ply < 120; ply++) {
                BitBoard old = BitBoardRules.fromFen(fen);
                List<int[]> expected = BitBoardRules.legalMoves(old);
                Board board = Boards.fromFen(fen);
                int[] moves = board.legalMoves();
                assertEquals(codes(expected), asList(moves), fen);
                if (moves.length == 0) {
                    break;
                }
                for (int[] m : expected) {
                    String want = BitBoardRules.applyMove(BitBoardRules.fromFen(fen), m[0], m[1], m[2]);
                    String got = Boards.fromFen(fen).play(Move.of(m[0], m[1], (char) m[2])).toFen();
                    assertEquals(want, got, fen + " " + m[0] + "-" + m[1]);
                }
                int[] pick = expected.get(random.nextInt(expected.size()));
                fen = BitBoardRules.applyMove(BitBoardRules.fromFen(fen), pick[0], pick[1], pick[2]);
            }
        }
    }

    private static List<Integer> codes(List<int[]> moves) {
        List<Integer> out = new ArrayList<>();
        for (int[] m : moves) {
            out.add(Move.of(m[0], m[1], (char) m[2]));
        }
        return out;
    }

    private static List<Integer> asList(int[] moves) {
        List<Integer> out = new ArrayList<>();
        for (int m : moves) {
            out.add(m);
        }
        return out;
    }
}
