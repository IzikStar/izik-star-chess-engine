package ai.BitBoard;

import ai.eval.ParamVector;
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
    private static List<BitBoard> positions(long seed, int count) {
        Random random = new Random(seed);
        List<BitBoard> out = new ArrayList<>();
        BitBoard b = BitBoardRules.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        while (out.size() < count) {
            List<BitBoard> next = b.getNextStates();
            if (next.isEmpty() || b.getStatus() != 1) {
                b = BitBoardRules.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                continue;
            }
            b = next.get(random.nextInt(next.size()));
            out.add(b);
        }
        return out;
    }

    private static int[] scores(BitBoardEvaluate eval, List<BitBoard> boards) {
        int[] s = new int[boards.size()];
        for (int i = 0; i < s.length; i++) {
            s[i] = eval.evaluate(boards.get(i), i % 2 == 0);
        }
        return s;
    }

    @Test
    @DisplayName("A different weight changes the score; the same weights give the same score")
    void weightsMatter() {
        BitBoard b = BitBoardRules.fromFen(QUEEN_UP);
        BitBoardEvaluate cheapQueen = new BitBoardEvaluate(BitBoardEvaluate.SCHEMA.defaults().with("material.queen", 0));
        int normal = BitBoardEvaluate.DEFAULT.evaluate(b, true);
        assertNotEquals(normal, cheapQueen.evaluate(b, true));
        assertEquals(normal, new BitBoardEvaluate(BitBoardEvaluate.SCHEMA.defaults()).evaluate(b, true));
    }

    @Test
    @DisplayName("Two weight sets evaluating on several threads at once get the same scores as alone")
    void noSharedState() throws Exception {
        List<BitBoard> boards = positions(5, 3000);
        ParamVector other = BitBoardEvaluate.SCHEMA.defaults()
                .with("development.openingUntilTurn", 0) // flips the game stage the old static held
                .with("development.queenOutEarly", 60)
                .with("material.knight", 45);
        BitBoardEvaluate a = BitBoardEvaluate.DEFAULT;
        BitBoardEvaluate b = new BitBoardEvaluate(other);
        int[] aloneA = scores(a, boards);
        int[] aloneB = scores(b, boards);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<int[]>> runs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                BitBoardEvaluate e = i % 2 == 0 ? a : b;
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
