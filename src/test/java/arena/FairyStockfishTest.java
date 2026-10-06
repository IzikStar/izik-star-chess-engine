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
        assertEquals(Optional.of("amazon-chess"), FairyStockfish.variantName(TestVariants.AMAZON_CHESS));
        assertFalse(FairyStockfish.plays(TWO_CHECK));
    }

    /** A game Fairy-Stockfish has no config for: three-check is the only check count it is given. */
    static final ai.variant.Variant TWO_CHECK = new ai.variant.Variant("two-check", "Two-check",
            ai.piece.StandardPieces.ALL, ai.piece.Grid.CHESS, Variants.CHESS.startFen(),
            ai.variant.Variant.Goal.CHECKS, 2, false, true);

    @Test
    @DisplayName("A made variant is written as a config section: base game by goal, missing chess pieces off, invented ones as Betza")
    void config() {
        String amazon = FairyConfig.of(TestVariants.made("amazon-antichess")).orElseThrow();
        assertTrue(amazon.startsWith("[amazon-antichess:antichess]\n"), amazon);
        assertTrue(amazon.contains("queen = -\n"), amazon);
        assertTrue(amazon.contains("customPiece1 = a:"), amazon);
        assertTrue(amazon.contains("mustCapture = true\n"), amazon);
        assertTrue(amazon.contains("startFen = rnbakbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBAKBNR w - - 0 1\n"), amazon);
        assertFalse(amazon.contains("rook = -"), amazon);
        assertEquals(Optional.empty(), FairyConfig.of(TWO_CHECK));
        // a king that is not the base game's: Fairy-Stockfish would keep its own, so no config
        ai.piece.PieceType k = ai.piece.StandardPieces.KING;
        ai.piece.PieceType knightKing = new ai.piece.PieceType("King", 'K', ai.piece.StandardPieces.KNIGHT.atoms(),
                true, List.of(), false, ai.piece.PieceType.Castling.NONE, k.value());
        List<ai.piece.PieceType> pieces = new java.util.ArrayList<>(ai.piece.StandardPieces.ALL);
        pieces.replaceAll(p -> p.letter() == 'K' ? knightKing : p);
        ai.variant.Variant odd = new ai.variant.Variant("knight-king", "Knight king", pieces, ai.piece.Grid.CHESS,
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", ai.variant.Variant.Goal.CHECKMATE, 0, false, false);
        assertEquals(Optional.empty(), FairyConfig.of(odd));
    }

    @Test
    @DisplayName("Fairy-Stockfish plays every made test variant by our rules and beats the random mover (when it is installed)")
    void fairyPlaysMadeVariants() {
        assumeTrue(FairyStockfishLocator.find().isPresent(), "Fairy-Stockfish is not installed");
        for (String id : List.of("amazon-antichess", "amazon-chess", "archbishop-chess", "forward-chess", "knightrider-chess")) {
            ai.variant.Variant variant = TestVariants.made(id);
            if (!FairyStockfish.plays(variant)) {
                continue; // a piece the config cannot carry (Betza i): nothing to check
            }
            Player fsf = Players.parse("fsf:2000", "fsf", 3, 0, Players.HALL_OF_FAME, variant);
            Player random = Players.random("random", variant);
            for (long seed = 1; seed <= 2; seed++) {
                GameRecord game = Match.play(variant, random, fsf, new Opening("none", List.of()), 300, seed);
                Game replay = new Game(variant);
                game.moves().forEach(replay::play); // every Fairy-Stockfish move is legal by our rules
                assertEquals(GameRecord.Result.BLACK_WINS, game.result(), id + ": " + game.reason());
            }
        }
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
