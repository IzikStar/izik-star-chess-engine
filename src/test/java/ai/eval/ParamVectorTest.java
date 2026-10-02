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
        assertEquals(10, d.get("material.pawn"));
        assertEquals(90, d.get("material.queen"));
        assertEquals(-25, d.get("king.exposed"));
        assertEquals(30, schema.size());
    }

    @Test
    @DisplayName("Values are kept inside each parameter's range")
    void clamped() {
        ParamVector v = schema.defaults().with("material.pawn", 5000).with("material.knight", -3);
        assertEquals(100, v.get("material.pawn"));
        assertEquals(0, v.get("material.knight"));
    }

    @Test
    @DisplayName("JSON round-trips; missing names keep their defaults; unknown names are refused")
    void json() {
        ParamVector v = schema.defaults().with("material.rook", 55).with("king.exposed", -40);
        assertEquals(v, ParamVector.fromJson(schema, v.toJson()));

        ParamVector partial = ParamVector.fromJson(schema, "{\"material.queen\": 95}");
        assertEquals(95, partial.get("material.queen"));
        assertEquals(10, partial.get("material.pawn"));

        assertThrows(IllegalArgumentException.class, () -> ParamVector.fromJson(schema, "{\"material.qeen\": 95}"));
    }

    @Test
    @DisplayName("A vector is immutable: with() returns a new one")
    void immutable() {
        ParamVector d = schema.defaults();
        ParamVector changed = d.with(0, 20);
        assertEquals(10, d.get(0));
        assertEquals(20, changed.get(0));
        assertNotEquals(d, changed);
        int[] copy = d.toArray();
        copy[0] = 99;
        assertEquals(10, d.get(0));
    }

    @Test
    @DisplayName("A spec's default must lie in its range, and names are unique")
    void specChecks() {
        assertThrows(IllegalArgumentException.class, () -> new ParamSpec("x", "g", 5, 6, 10, ""));
        ParamSpec a = new ParamSpec("x", "g", 0, 0, 1, "");
        assertThrows(IllegalArgumentException.class, () -> new ParamSchema(java.util.List.of(a, a)));
    }
}
