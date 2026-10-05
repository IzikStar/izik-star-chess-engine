package ai.variant;

import ai.piece.Grid;
import ai.piece.StandardPieces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 R4a: a variant is data, saved and read back as JSON without losing anything. */
class VariantTest {

    @Test
    @DisplayName("Every built-in variant reads back from its JSON exactly")
    void jsonRoundTrip() {
        for (Variant v : Variants.ALL) {
            String json = VariantJson.write(v);
            assertEquals(v, VariantJson.read(json), json);
        }
    }

    @Test
    @DisplayName("A missing field is named in the error")
    void missingField() {
        String json = VariantJson.write(Variants.CHESS).replace("\"goal\"", "\"aim\"");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> VariantJson.read(json));
        assertTrue(e.getMessage().contains("goal"), e.getMessage());
    }

    @Test
    @DisplayName("Inconsistent variants are refused")
    void validation() {
        String start = Variants.CHESS.startFen();
        // three checks without the CHECKS goal, and the CHECKS goal without a count
        assertThrows(IllegalArgumentException.class, () -> new Variant("x", "X", StandardPieces.ALL, Grid.CHESS, start,
                Variant.Goal.CHECKMATE, 3, false, true));
        assertThrows(IllegalArgumentException.class, () -> new Variant("x", "X", StandardPieces.ALL, Grid.CHESS, start,
                Variant.Goal.CHECKS, 0, false, true));
        // checkmate needs a royal piece; antichess does not
        assertThrows(IllegalArgumentException.class, () -> new Variant("x", "X", List.of(StandardPieces.QUEEN),
                Grid.CHESS, "8/8/8/8/8/8/8/Q7 w - - 0 1", Variant.Goal.CHECKMATE, 0, false, false));
        new Variant("x", "X", List.of(StandardPieces.QUEEN), Grid.CHESS, "q7/8/8/8/8/8/8/Q7 w - - 0 1",
                Variant.Goal.LOSE_EVERYTHING, 0, true, false);
        // two pieces with one letter, an id with spaces
        assertThrows(IllegalArgumentException.class, () -> new Variant("x", "X",
                List.of(StandardPieces.KING, StandardPieces.KING), Grid.CHESS, start, Variant.Goal.CHECKMATE, 0, false, true));
        assertThrows(IllegalArgumentException.class, () -> new Variant("my variant", "X", StandardPieces.ALL,
                Grid.CHESS, start, Variant.Goal.CHECKMATE, 0, false, true));
    }

    @Test
    @DisplayName("Built-ins are found by id")
    void byId() {
        assertEquals(Variants.ANTICHESS, Variants.byId("antichess").orElseThrow());
        assertTrue(Variants.byId("nope").isEmpty());
    }
}
