package arena;

import ai.variant.TestVariants;
import ai.variant.Variants;
import engine.FairyStockfishLocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Game;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Fairy-Stockfish and the random mover as players, for yardsticks outside chess. */
class FairyStockfishTest {

    @Test
    @DisplayName("Fairy-Stockfish knows each built-in variant by its own name, and no made variant yet")
    void names() {
        assertEquals(Optional.of("chess"), FairyStockfish.variantName(Variants.CHESS));
        assertEquals(Optional.of("antichess"), FairyStockfish.variantName(Variants.ANTICHESS));
        assertEquals(Optional.of("kingofthehill"), FairyStockfish.variantName(Variants.KING_OF_THE_HILL));
        assertEquals(Optional.of("3check"), FairyStockfish.variantName(Variants.THREE_CHECK));
        assertFalse(FairyStockfish.plays(TestVariants.AMAZON_CHESS));
    }

    @Test
    @DisplayName("fsf, fsf:N and random are players without weights; their labels name the nodes")
    void specs() {
        assertTrue(Players.isFairyStockfish("fsf"));
        assertTrue(Players.isFairyStockfish("fsf:5000"));
        assertFalse(Players.isFairyStockfish("fsfoo.json"));
        assertFalse(Players.hasWeights("fsf"));
        assertFalse(Players.hasWeights("random"));
        assertFalse(Players.hasWeights("sf:1500"));
        assertTrue(Players.hasWeights("zero"));
        assertEquals("fsf20000", Players.label("fsf"));
        assertEquals("fsf5000", Players.label("fsf:5000"));
        assertThrows(IllegalArgumentException.class, () -> Players.label("fsf:0"));
    }

    @Test
    @DisplayName("Two random movers finish an antichess game, every move legal, and differ by seed")
    void randomMovers() {
        Player a = Players.parse("random", "a", 3, 0, Players.HALL_OF_FAME, Variants.ANTICHESS);
        Player b = Players.parse("random", "b", 3, 0, Players.HALL_OF_FAME, Variants.ANTICHESS);
        assertEquals(1, a.depth());
        Opening none = new Opening("none", List.of());
        GameRecord first = Match.play(Variants.ANTICHESS, a, b, none, 300, 1);
        GameRecord second = Match.play(Variants.ANTICHESS, a, b, none, 300, 2);
        assertFalse(first.moves().equals(second.moves()));
        Game replay = new Game(Variants.ANTICHESS);
        first.moves().forEach(replay::play); // throws on an illegal move
    }

    @Test
    @DisplayName("Outside chess an engine from outside must be Fairy-Stockfish set to that variant")
    void onlyFairyOutsideChess() {
        Player stockfishLike = Player.external("sf", new ExternalEngine("stockfish", Map.of(), 1000, 0));
        Player chessFairy = Player.external("fsf", new ExternalEngine("fairy-stockfish",
                Map.of("UCI_Variant", "chess"), 1000, 0));
        Player random = Players.random("random", Variants.ANTICHESS);
        Opening none = new Opening("none", List.of());
        assertThrows(IllegalArgumentException.class, () -> Match.play(Variants.ANTICHESS, stockfishLike, random, none, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> Match.play(Variants.ANTICHESS, random, chessFairy, none, 10, 1));
    }

    @Test
    @DisplayName("Fairy-Stockfish beats the random mover at antichess and three-check (when it is installed)")
    void fairyPlays() {
        assumeTrue(FairyStockfishLocator.find().isPresent(), "Fairy-Stockfish is not installed");
        for (ai.variant.Variant variant : List.of(Variants.ANTICHESS, Variants.THREE_CHECK, Variants.KING_OF_THE_HILL)) {
            Player fsf = Players.parse("fsf:2000", "fsf", 3, 0, Players.HALL_OF_FAME, variant);
            Player random = Players.random("random", variant);
            GameRecord game = Match.play(variant, random, fsf, new Opening("none", List.of()), 300, 3);
            assertEquals(GameRecord.Result.BLACK_WINS, game.result(), variant.name() + ": " + game.reason());
            Game replay = new Game(variant);
            game.moves().forEach(replay::play);
        }
    }
}
