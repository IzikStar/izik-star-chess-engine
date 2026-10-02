package arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningsTest {

    private static final List<Opening> SUITE = Opening.suite();

    @Test
    @DisplayName("The suite has about fifty openings with different names and positions")
    void aboutFiftyDistinctOpenings() {
        assertTrue(SUITE.size() >= 45, "only " + SUITE.size() + " openings");
        Set<String> names = new HashSet<>();
        Set<String> positions = new HashSet<>();
        for (Opening opening : SUITE) {
            names.add(opening.name());
            Game game = new Game();
            opening.moves().forEach(game::play);
            positions.add(game.fen());
        }
        assertEquals(SUITE.size(), names.size(), "names repeat");
        assertEquals(SUITE.size(), positions.size(), "two openings reach the same position");
    }

    @Test
    @DisplayName("Every opening is legal and leaves the game going, White to move")
    void everyLineIsLegal() {
        for (Opening opening : SUITE) {
            Game game = new Game();
            opening.moves().forEach(game::play); // throws on an illegal move
            assertFalse(game.status().isGameOver(), opening.name());
            assertTrue(game.fen().split(" ")[1].equals("w"), opening.name() + " ends with Black to move");
        }
    }
}
