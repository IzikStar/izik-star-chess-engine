package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The difficulty ladder (docs/difficulty-ladder.md). */
class LevelsTest {

    @Test
    @DisplayName("Levels 0-8 are the built-in engine, 9-13 Stockfish")
    void whoPlays() {
        for (int level = 0; level <= 8; level++) {
            assertFalse(Levels.isStockfish(level), "level " + level);
        }
        for (int level = 9; level <= 13; level++) {
            assertTrue(Levels.isStockfish(level), "level " + level);
        }
    }

    @Test
    @DisplayName("Levels 1-3 are depth 1 with fewer random moves, then depths 2, 3, 4, 6, 7")
    void builtInLevels() {
        assertEquals(100, Levels.randomPercent(0));
        int[] depth = {0, 1, 1, 1, 2, 3, 4, 6, 7};
        int[] random = {100, 25, 12, 0, 0, 0, 0, 0, 0};
        for (int level = 1; level <= 8; level++) {
            assertEquals(depth[level], Levels.builtInDepth(level), "level " + level);
            assertEquals(random[level], Levels.randomPercent(level), "level " + level);
        }
    }

    @Test
    @DisplayName("Stockfish's levels rise 250 Elo at a time, then full strength")
    void stockfishLevels() {
        assertEquals(2150, Levels.stockfishElo(9));
        assertEquals(2400, Levels.stockfishElo(10));
        assertEquals(2650, Levels.stockfishElo(11));
        assertEquals(2900, Levels.stockfishElo(12));
        assertEquals(0, Levels.stockfishElo(13));
        assertEquals(0, Levels.stockfishElo(Levels.HINT));
        assertEquals(500, Levels.stockfishMoveTimeMs(9));
        assertEquals(1000, Levels.stockfishMoveTimeMs(13));
        assertEquals(4000, Levels.stockfishMoveTimeMs(Levels.HINT));
    }
}
