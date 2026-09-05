package characterization;

import main.savedGames.SavedStatesForDraws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the current mechanics of the two draw-related helpers, so Phase 2 can tell
 * "changed on purpose" from "regressed". Neither helper is consulted by the engines'
 * status calls today — see {@link KnownBugsTest}.
 */
class RepetitionAndMaterialTest extends CharacterizationTestBase {

    // ---- SavedStatesForDraws (static threefold-repetition history) --------

    @Test
    @DisplayName("Repetition: the same key seen 3x is a repetition, 2x is not")
    void threeOccurrencesIsRepetition() {
        SavedStatesForDraws.addState("pos-A");
        SavedStatesForDraws.addState("pos-A");
        assertFalse(SavedStatesForDraws.isRepetition());
        SavedStatesForDraws.addState("pos-A");
        assertTrue(SavedStatesForDraws.isRepetition());
    }

    @Test
    @DisplayName("Repetition: interleaved keys are counted independently")
    void interleavedKeys() {
        SavedStatesForDraws.addState("A");
        SavedStatesForDraws.addState("B");
        SavedStatesForDraws.addState("A");
        SavedStatesForDraws.addState("B");
        assertFalse(SavedStatesForDraws.isRepetition());
        SavedStatesForDraws.addState("A");
        assertTrue(SavedStatesForDraws.isRepetition());
    }

    @Test
    @DisplayName("Repetition: removeLastState() undoes the triggering entry")
    void removeLastStateUndoesRepetition() {
        SavedStatesForDraws.addState("A");
        SavedStatesForDraws.addState("A");
        SavedStatesForDraws.addState("A");
        assertTrue(SavedStatesForDraws.isRepetition());
        SavedStatesForDraws.removeLastState();
        assertFalse(SavedStatesForDraws.isRepetition());
    }

    // ---- BoardState.insufficientMaterial(isWhite) ------------------------

    @Test
    @DisplayName("Material: lone king is insufficient")
    void loneKing() {
        assertTrue(oo("k7/8/8/8/8/8/8/7K w - - 0 1").insufficientMaterial(true));
    }

    @Test
    @DisplayName("Material: K+B is insufficient")
    void kingAndBishop() {
        assertTrue(oo("k7/8/8/8/8/8/8/6BK w - - 0 1").insufficientMaterial(true));
    }

    @Test
    @DisplayName("CHARACTERIZED: K+B+B is treated as SUFFICIENT (size >= 3 rule)")
    void kingAndTwoBishops() {
        // insufficientMaterial() is "no Q/R/P and fewer than 3 pieces". K+N+N and K+B+B
        // therefore come back sufficient even though K+N+N vs K cannot be forced.
        assertFalse(oo("k7/8/8/8/8/8/8/5BBK w - - 0 1").insufficientMaterial(true));
    }

    @Test
    @DisplayName("Material: any pawn makes it sufficient")
    void withPawn() {
        assertFalse(oo("k7/7p/8/8/8/8/8/7K w - - 0 1").insufficientMaterial(false));
    }
}
