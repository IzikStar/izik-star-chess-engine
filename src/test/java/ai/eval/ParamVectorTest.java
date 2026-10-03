package ai.eval;

import ai.BitBoard.BitBoardEvaluate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParamVectorTest {

    private final ParamSchema schema = BitBoardEvaluate.SCHEMA;

    @Test
    @DisplayName("The defaults are the weights the evaluation used to hard-code")
    void defaults() {
        ParamVector d = schema.defaults();
        assertEquals(100, d.get("material.pawn.mg"));
        assertEquals(100, d.get("material.pawn.eg"));
        assertEquals(900, d.get("material.queen.mg"));
        assertEquals(-250, d.get("king.exposed.eg"));
        assertEquals(20, d.get("king.safetyUntilTurn"));
        assertEquals(0, d.get("pawns.passed.rank6.mg"));
        assertEquals(0, d.get("pst.knight.d4.eg"));
        // 56 named features × (middlegame, endgame) + 3 gates + 6 piece types × 32 squares × 2 phases
        assertEquals(56 * 2 + 3 + 6 * 32 * 2, schema.size());
    }

    @Test
    @DisplayName("Values are kept inside each parameter's range")
    void clamped() {
        ParamVector v = schema.defaults().with("material.pawn.mg", 50000).with("material.knight.mg", -3);
        assertEquals(3000, v.get("material.pawn.mg"));
        assertEquals(0, v.get("material.knight.mg"));
    }

    @Test
    @DisplayName("JSON round-trips; missing names keep their defaults; unknown names are refused")
    void json() {
        ParamVector v = schema.defaults().with("material.rook.eg", 55).with("king.exposed.mg", -40);
        assertEquals(v, ParamVector.fromJson(schema, v.toJson()));

        ParamVector partial = ParamVector.fromJson(schema, "{\"unit\": \"centipawn\", \"material.queen.mg\": 950}");
        assertEquals(950, partial.get("material.queen.mg"));
        assertEquals(900, partial.get("material.queen.eg"));

        assertThrows(IllegalArgumentException.class, () -> ParamVector.fromJson(schema, "{\"material.qeen\": 95}"));
    }

    @Test
    @DisplayName("A file from before the centipawn unit is read as tenths of a pawn: scores ×10, turn gates as they were")
    void tenthsOfAPawn() {
        ParamVector old = ParamVector.fromJson(schema, "{\"material.knight.mg\": 32, \"king.safetyUntilTurn\": 15}");
        assertEquals(320, old.get("material.knight.mg"));
        assertEquals(15, old.get("king.safetyUntilTurn"));
        assertEquals(300, old.get("material.knight.eg")); // left out: the default
        assertThrows(IllegalArgumentException.class, () -> ParamVector.fromJson(schema, "{\"unit\": \"pawn\"}"));
    }

    @Test
    @DisplayName("The classic preset is the hand-written weights, in centipawns")
    void classic() {
        ParamVector c = BitBoardEvaluate.preset("classic");
        assertEquals(c, BitBoardEvaluate.CLASSIC.params());
        assertEquals(100, c.get("material.pawn.mg"));
        assertEquals(330, c.get("material.bishop.eg"));
        assertEquals(8, c.get("development.openingUntilTurn"));
        assertEquals(0, c.get("pst.knight.d4.mg"));
    }

    @Test
    @DisplayName("A vector is immutable: with() returns a new one")
    void immutable() {
        ParamVector d = schema.defaults();
        ParamVector changed = d.with(0, 20);
        assertEquals(100, d.get(0));
        assertEquals(20, changed.get(0));
        assertNotEquals(d, changed);
        int[] copy = d.toArray();
        copy[0] = 99;
        assertEquals(100, d.get(0));
    }

    @Test
    @DisplayName("A spec's default must lie in its range, and names are unique")
    void specChecks() {
        assertThrows(IllegalArgumentException.class, () -> new ParamSpec("x", "g", 5, 6, 10, ""));
        ParamSpec a = new ParamSpec("x", "g", 0, 0, 1, "");
        assertThrows(IllegalArgumentException.class, () -> new ParamSchema(java.util.List.of(a, a)));
    }
}
