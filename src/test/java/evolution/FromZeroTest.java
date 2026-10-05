package evolution;

import ai.eval.Evaluators;
import ai.eval.ParamSchema;
import ai.eval.ParamVector;
import ai.variant.Variants;
import arena.GameRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FromZeroTest {

    private static final ParamSchema ANTI = Evaluators.schema(Variants.ANTICHESS);

    private static FromZero configured(Map<String, String> chosen) {
        FromZero f = new FromZero();
        Map<String, String> values = new java.util.HashMap<>(Evolution.resolve(f.options(), chosen));
        values.put("generations", "10");
        f.configure(values);
        return f;
    }

    @Test
    @DisplayName("The options are checked and the defaults filled in")
    void options() {
        FromZero f = new FromZero();
        Map<String, String> v = Evolution.resolve(f.options(), Map.of("population", "8"));
        assertEquals("8", v.get("population"));
        assertEquals("zero", v.get("start"));
        assertEquals(f.options().size(), v.size());
        assertThrows(IllegalArgumentException.class, () -> Evolution.resolve(f.options(), Map.of("population", "2")));
        assertThrows(IllegalArgumentException.class, () -> Evolution.resolve(f.options(), Map.of("start", "nowhere")));
        assertThrows(IllegalArgumentException.class, () -> Evolution.resolve(f.options(), Map.of("rate", "lots")));
        assertThrows(IllegalArgumentException.class, () -> Evolution.resolve(f.options(), Map.of("nosuch", "1")));
    }

    @Test
    @DisplayName("From zero: member 0 is all zeros, the others scattered, only the chosen weights move")
    void fromZero() {
        FromZero f = configured(Map.of("population", "6", "evolve", "material"));
        List<ParamVector> first = f.firstGeneration(ANTI, new Random(1));
        assertEquals(6, first.size());
        for (int i = 0; i < ANTI.size(); i++) {
            assertEquals(0, first.getFirst().get(i));
        }
        boolean moved = false;
        for (ParamVector m : first.subList(1, 6)) {
            for (int i = 0; i < ANTI.size(); i++) {
                if (!ANTI.spec(i).group().equals("material")) {
                    assertEquals(0, m.get(i), ANTI.spec(i).name() + " must not move");
                } else {
                    moved |= m.get(i) != 0;
                }
            }
        }
        assertTrue(moved);
    }

    @Test
    @DisplayName("Survivors stay unchanged, immigrants are fresh, children come from parents; the step shrinks")
    void nextGeneration() {
        FromZero f = configured(Map.of("population", "8", "survivors", "2", "immigrants", "1", "stepFirst", "100", "stepLast", "1"));
        List<ParamVector> first = f.firstGeneration(ANTI, new Random(2));
        // member 3 wins every game it plays, member 5 the rest
        List<GameRecord> games = new java.util.ArrayList<>();
        for (int a = 0; a < 8; a++) {
            for (int b = a + 1; b < 8; b++) {
                GameRecord.Result r = a == 3 ? GameRecord.Result.WHITE_WINS : b == 3 || b == 5 ? GameRecord.Result.BLACK_WINS
                        : a == 5 ? GameRecord.Result.WHITE_WINS : GameRecord.Result.DRAW;
                games.add(new GameRecord(String.valueOf(a), String.valueOf(b), "o", List.of(), r, "x", 1));
            }
        }
        Generation g = new Generation(0, first, games);
        assertEquals(3, g.champion());
        List<ParamVector> next = f.nextGeneration(g, new Random(3));
        assertEquals(8, next.size());
        assertEquals(first.get(3), next.get(0));
        assertEquals(first.get(5), next.get(1));
        assertTrue(first.stream().noneMatch(m -> m.equals(next.get(2))), "the immigrant is new");
        assertEquals(100, f.step(0), 1e-9);
        assertEquals(1, f.step(9), 1e-9);
        assertTrue(f.step(4) > f.step(5));
    }

    @Test
    @DisplayName("Starting from the defaults keeps them as member 0; 'all' evolves every weight")
    void defaultsAndAll() {
        FromZero f = configured(Map.of("population", "4", "start", "defaults", "evolve", "all"));
        ParamSchema chess = Evaluators.schema(Variants.CHESS);
        List<ParamVector> first = f.firstGeneration(chess, new Random(4));
        assertEquals(chess.defaults(), first.getFirst());
        assertNotEquals(chess.defaults(), first.get(1));
        assertTrue(f.evolves(chess.spec(chess.size() - 1)));
    }
}
