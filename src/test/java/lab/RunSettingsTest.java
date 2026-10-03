package lab;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunSettingsTest {

    @Test
    @DisplayName("Settings round-trip through JSON; a run saved before yardstick lists and deep games plays as it did")
    void json() {
        RunSettings d = RunSettings.defaults();
        assertEquals(d, RunSettings.fromJson(d.toJson()));

        RunSettings old = RunSettings.fromJson("{\"generations\":5,\"depth\":3,\"openingsPerPairing\":2,\"variety\":2,"
                + "\"maxPlies\":300,\"threads\":3,\"seed\":1,\"yardstickEvery\":5,\"yardstickOpenings\":20}");
        assertEquals(List.of("default"), old.yardsticks());
        assertEquals(0, old.deepDepth());
        assertEquals(0, old.deepShare(4));
    }

    @Test
    @DisplayName("The share of deep games grows evenly from the first generation to the last")
    void deepShare() {
        RunSettings s = new RunSettings(5, 3, 2, 20, 300, 2, 1, 5, 20, List.of("default"), 0, 4, 10, 50);
        assertEquals(List.of(10, 20, 30, 40, 50), List.of(s.deepShare(0), s.deepShare(1), s.deepShare(2),
                s.deepShare(3), s.deepShare(4)));
    }

    @Test
    @DisplayName("Stockfish yardsticks join from the given generation; a yardstick listed twice is refused")
    void yardsticks() {
        RunSettings s = new RunSettings(20, 3, 2, 20, 300, 2, 1, 5, 20, List.of("default", "sf:auto", "sf:1500"), 10,
                0, 0, 0);
        assertEquals(List.of("default"), s.yardsticksAt(9));
        assertEquals(List.of("default", "sf:auto", "sf:1500"), s.yardsticksAt(10));
        assertThrows(IllegalArgumentException.class, () -> new RunSettings(20, 3, 2, 20, 300, 2, 1, 5, 20,
                List.of("classic", "classic"), 0, 0, 0, 0));
    }
}
