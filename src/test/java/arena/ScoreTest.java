package arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreTest {

    @Test
    @DisplayName("Even is 0 Elo, 75% about +191, and the extremes are capped")
    void eloOfAScore() {
        assertEquals(0, new Score("x", 5, 0, 5).elo(), 1e-9);
        assertEquals(191, new Score("x", 75, 0, 25).elo(), 1);
        assertEquals(-191, new Score("x", 25, 0, 75).elo(), 1);
        assertEquals(Score.ELO_LIMIT, new Score("x", 10, 0, 0).elo());
        assertEquals(-Score.ELO_LIMIT, new Score("x", 0, 0, 10).elo());
    }

    @Test
    @DisplayName("The interval surrounds the result and narrows with more games")
    void intervalNarrows() {
        Score few = new Score("x", 11, 0, 9);
        Score many = new Score("x", 110, 0, 90);
        assertEquals(few.elo(), many.elo(), 1e-9);
        assertTrue(few.eloLow() < few.elo() && few.elo() < few.eloHigh());
        assertTrue(many.eloHigh() - many.eloLow() < few.eloHigh() - few.eloLow());
        // 55% over 200 games: about +35, with roughly -15 to +85 just as likely
        assertEquals(35, many.elo(), 1);
        assertEquals(-14, many.eloLow(), 3);
        assertEquals(84, many.eloHigh(), 3);
    }

    @Test
    @DisplayName("Draws count half a point and shrink the spread")
    void drawsCountHalf() {
        Score drawn = new Score("x", 0, 10, 0);
        assertEquals(0.5, drawn.fraction());
        assertEquals(0, drawn.standardError(), 1e-12);
    }
}
