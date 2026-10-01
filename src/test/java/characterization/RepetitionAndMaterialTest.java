package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Game;
import rules.Rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Threefold repetition and insufficient material. Phase 3 deleted the two legacy trackers these
 * used to pin ({@code SavedStatesForDraws}, a static list of draw-FEN strings, and
 * {@code BoardState.insufficientMaterial}); the same scenarios now run against {@link Game} and
 * {@link Rules}. One behaviour deliberately differs: see {@link #kingAndTwoBishops()}.
 */
class RepetitionAndMaterialTest extends CharacterizationTestBase {

    private static final String[] KNIGHT_SHUFFLE = {"g1f3", "g8f6", "f3g1", "f6g8"};

    // ---- threefold repetition (rules.Game history) ------------------------

    @Test
    @DisplayName("Repetition: the same position 3x is a repetition, 2x is not")
    void threeOccurrencesIsRepetition() {
        Game g = new Game();
        for (String m : KNIGHT_SHUFFLE) {
            g.play(m);
        }
        assertFalse(g.isThreefoldRepetition(), "start position seen twice");
        for (String m : KNIGHT_SHUFFLE) {
            g.play(m);
        }
        assertTrue(g.isThreefoldRepetition(), "start position seen three times");
    }

    @Test
    @DisplayName("Repetition: interleaved positions are counted independently")
    void interleavedPositions() {
        Game g = new Game();
        g.play("g1f3");
        g.play("g8f6");
        g.play("f3g1");
        g.play("f6g8"); // start x2
        g.play("b1c3");
        g.play("b8c6");
        g.play("c3b1");
        g.play("c6b8"); // start x3, but via a different shuffle
        assertTrue(g.isThreefoldRepetition());
    }

    @Test
    @DisplayName("Repetition: undo() takes back the triggering position")
    void undoUndoesRepetition() {
        Game g = new Game();
        for (int i = 0; i < 2; i++) {
            for (String m : KNIGHT_SHUFFLE) {
                g.play(m);
            }
        }
        assertTrue(g.isThreefoldRepetition());
        g.undo();
        assertFalse(g.isThreefoldRepetition());
    }

    // ---- insufficient material (rules.Rules) ------------------------------

    @Test
    @DisplayName("Material: lone king vs lone king is insufficient")
    void loneKing() {
        assertTrue(Rules.isInsufficientMaterial("k7/8/8/8/8/8/8/7K w - - 0 1"));
    }

    @Test
    @DisplayName("Material: K+B vs K is insufficient")
    void kingAndBishop() {
        assertTrue(Rules.isInsufficientMaterial("k7/8/8/8/8/8/8/6BK w - - 0 1"));
    }

    @Test
    @DisplayName("Material: K+B+B vs K is sufficient (mate can be forced)")
    void kingAndTwoBishops() {
        assertFalse(Rules.isInsufficientMaterial("k7/8/8/8/8/8/8/5BBK w - - 0 1"));
    }

    @Test
    @DisplayName("Material: any pawn makes it sufficient")
    void withPawn() {
        assertFalse(Rules.isInsufficientMaterial("k7/7p/8/8/8/8/8/7K w - - 0 1"));
    }
}
