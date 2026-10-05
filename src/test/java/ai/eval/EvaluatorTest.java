package ai.eval;

import ai.board.Board;
import ai.board.Boards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Phase 5 step 1: the evaluation reads its weights from a parameter vector and shares no state. */
class EvaluatorTest {

    /** White is a queen up, so the queen's value shows in the score. */
    private static final String QUEEN_UP = "4k3/pppp4/8/8/8/8/PPPP4/3QK3 w - - 0 1";

    /** Positions from seeded random games, to evaluate in bulk. */
    private static List<Board> positions(long seed, int count) {
        Random random = new Random(seed);
        List<Board> out = new ArrayList<>();
        Board b = Boards.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        while (out.size() < count) {
            List<? extends Board> next = b.children();
            if (next.isEmpty() || b.isOver()) {
                b = Boards.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                continue;
            }
            b = next.get(random.nextInt(next.size()));
            out.add(b);
        }
        return out;
    }

    private static int[] scores(ChessEvaluate eval, List<Board> boards) {
        int[] s = new int[boards.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = eval.evaluate(boards.get(i), i % 2 == 0 ? 1 : 0);
        }
        return s;
    }

    @Test
    @DisplayName("A different weight changes the score; the same weights give the same score")
    void weightsMatter() {
        Board b = Boards.fromFen(QUEEN_UP);
        ChessEvaluate cheapQueen = new ChessEvaluate(ChessEvaluate.SCHEMA.defaults().with("material.queen.mg", 0).with("material.queen.eg", 0));
        int normal = ChessEvaluate.DEFAULT.evaluate(b, 1);
        assertNotEquals(normal, cheapQueen.evaluate(b, 1));
        assertEquals(normal, new ChessEvaluate(ChessEvaluate.SCHEMA.defaults()).evaluate(b, 1));
    }

    @Test
    @DisplayName("Two weight sets evaluating on several threads at once get the same scores as alone")
    void noSharedState() throws Exception {
        List<Board> boards = positions(5, 3000);
        ParamVector other = ChessEvaluate.SCHEMA.defaults()
                .with("development.openingUntilTurn", 0) // flips the game stage the old static held
                .with("development.queenOutEarly.mg", -60)
                .with("material.knight.eg", 45)
                .with("pawns.passed.rank6.eg", 80)
                .with("kingSafety.queenAttacker.mg", 20)
                .with("mobility.bishop.mg", 3)
                .with("pst.knight.d4.mg", 15);
        ChessEvaluate a = ChessEvaluate.DEFAULT;
        ChessEvaluate b = new ChessEvaluate(other);
        int[] aloneA = scores(a, boards);
        int[] aloneB = scores(b, boards);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<int[]>> runs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                ChessEvaluate e = i % 2 == 0 ? a : b;
                runs.add(pool.submit(() -> scores(e, boards)));
            }
            for (int i = 0; i < runs.size(); i++) {
                assertArrayEquals(i % 2 == 0 ? aloneA : aloneB, runs.get(i).get(), "run " + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
