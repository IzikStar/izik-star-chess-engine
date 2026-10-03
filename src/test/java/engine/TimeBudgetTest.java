package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Position;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The clock's limit on the engine's thinking ({@link TimeBudget}). */
class TimeBudgetTest {

    private static final String MIDDLEGAME =
            "r1bq1rk1/pp2bppp/2n1pn2/3p4/2PP4/2N1PN2/PP1B1PPP/R2QKB1R w KQ - 0 9";

    @Test
    @DisplayName("A slice of the time left plus most of the increment, never more than half of what is left")
    void budgets() {
        assertEquals(59_000 / 25, TimeBudget.forMove(60_000, 0), "1+0 at the start: about 2.4 s");
        assertEquals(179_000 / 25 + 1_500, TimeBudget.forMove(180_000, 2_000), "3+2 at the start");
        assertEquals(9_000 / 25, TimeBudget.forMove(10_000, 0), "10 s left: 0.36 s");
        assertEquals(900, TimeBudget.forMove(2_000, 5_000), "a big increment still bets at most half");
        assertEquals(TimeBudget.MIN_MS, TimeBudget.forMove(0, 0), "even out of time it gets a moment");
        assertTrue(TimeBudget.forMove(30_000, 0) < TimeBudget.forMove(60_000, 0), "less time, less thought");
    }

    @Test
    @DisplayName("The clock only shortens a level's thinking; without a budget the level's own time stands")
    void capOnlyShortens() {
        assertEquals(5_000, TimeBudget.cap(5_000, TimeBudget.NONE));
        assertEquals(1_200, TimeBudget.cap(5_000, 1_200));
        assertEquals(500, TimeBudget.cap(500, 9_000));
    }

    @Test
    @DisplayName("The built-in engine's top level moves within a short budget")
    void builtInKeepsToTheBudget() {
        MinimaxEngine engine = new MinimaxEngine(new Random(1));
        SearchRequest request = new SearchRequest(MIDDLEGAME, MIDDLEGAME, java.util.List.of(), Levels.TOP_BUILT_IN,
                Cancellation.NONE, 200);
        long start = System.nanoTime();
        assertNotNull(engine.bestMove(request));
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 1_000, "took " + ms + " ms on a 200 ms budget");
    }

    @Test
    @DisplayName("Changing the level keeps the budget (Stockfish's fallback to the built-in engine)")
    void levelChangeKeepsBudget() {
        SearchRequest r = SearchRequest.of(Position.START_FEN, 9).withTimeBudgetMs(300);
        assertEquals(300, r.withSkillLevel(Levels.TOP_BUILT_IN).timeBudgetMs());
        assertEquals(TimeBudget.NONE, SearchRequest.of(Position.START_FEN, 9).timeBudgetMs());
    }
}
