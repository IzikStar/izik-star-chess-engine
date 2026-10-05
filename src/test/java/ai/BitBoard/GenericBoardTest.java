package ai.BitBoard;

import ai.board.Board;
import ai.board.BoardRules;
import ai.board.GenericBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 6 R3: the generic board, playing chess from pieces as data, against the bitboard it is to
 * replace. Perft counts equal the published ones; along random games every position has the same
 * moves, next positions, check, game over, evaluation, quiescence moves and capture scores.
 */
class GenericBoardTest {

    static Stream<PerftPositions.Position> positions() {
        return Stream.concat(PerftPositions.STANDARD.stream(), PerftPositions.EDGE.stream());
    }

    private static GenericBoard generic(String fen) {
        return GenericBoard.fromFen(BoardRules.CHESS, fen);
    }

    static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }
        List<? extends Board> children = board.children();
        if (depth == 1) {
            return children.size();
        }
        long count = 0;
        for (Board child : children) {
            count += perft(child, depth - 1);
        }
        board.releaseChildren();
        return count;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("Perft counts equal the published ones")
    void perftCounts(PerftPositions.Position p) {
        int depth = p.depthWithin(2_000_000);
        assertEquals(p.count(depth), perft(generic(p.fen()), depth), p.name() + " depth " + depth);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("Along random games every position reads the same as on the bitboard")
    void sameAsBitboard(PerftPositions.Position start) {
        Random random = new Random(63);
        for (int game = 0; game < 10; game++) {
            String fen = start.fen();
            for (int ply = 0; ply < 150; ply++) {
                BitBoard old = BitBoardRules.fromFen(fen);
                GenericBoard now = generic(fen);
                assertEquals(fen, now.toFen(), "FEN round trip");
                assertEquals(byMove(old.children()), byMove(now.children()), fen);
                assertEquals(old.inCheck(), now.inCheck(), fen);
                assertEquals(old.isOver(), now.isOver(), fen);
                assertEquals(old.hasLegalMove(), now.hasLegalMove(), fen);
                for (int player = 0; player < 2; player++) {
                    assertEquals(old.attackedBy(player), now.attackedBy(player), fen);
                    assertEquals(BitBoardEvaluate.DEFAULT.evaluate(old, player),
                            BitBoardEvaluate.DEFAULT.evaluate(now, player), fen);
                    assertEquals(BitBoardEvaluate.CLASSIC.evaluate(old, player),
                            BitBoardEvaluate.CLASSIC.evaluate(now, player), fen);
                }
                assertEquals(noisy(old), noisy(now), "quiescence moves of " + fen);
                Map<Integer, Integer> oldScores = captureScores(old);
                assertEquals(oldScores, captureScores(now), fen);
                if (old.children().isEmpty()) {
                    break;
                }
                List<String> fens = new ArrayList<>(byMove(old.children()).values());
                fen = fens.get(random.nextInt(fens.size()));
            }
        }
    }

    private static Map<Integer, String> byMove(List<? extends Board> children) {
        Map<Integer, String> out = new TreeMap<>();
        for (Board child : children) {
            out.put(child.lastMove(), child.toFen());
        }
        return out;
    }

    /** Quiescence moves in order, ties (same value) as sets. */
    private static List<Integer> noisy(Board board) {
        List<Integer> moves = new ArrayList<>();
        for (Board child : board.noisyChildren()) {
            moves.add(child.lastMove());
        }
        moves.sort(null);
        return moves;
    }

    private static Map<Integer, Integer> captureScores(Board board) {
        Map<Integer, Integer> out = new TreeMap<>();
        for (Board child : board.children()) {
            out.put(child.lastMove(), child.captureScore(board));
        }
        return out;
    }
}
