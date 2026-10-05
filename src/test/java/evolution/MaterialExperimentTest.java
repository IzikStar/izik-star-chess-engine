package evolution;

import ai.eval.ChessEvaluate;
import ai.eval.ParamSchema;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.GameRecord.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialExperimentTest {

    private static final ParamSchema SCHEMA = ChessEvaluate.SCHEMA;
    private final MaterialExperiment experiment = new MaterialExperiment();

    private static int changed(ParamVector a, ParamVector b, boolean material, int[] largest) {
        int n = 0;
        for (int i = 0; i < SCHEMA.size(); i++) {
            if (a.get(i) != b.get(i) && SCHEMA.spec(i).group().equals("material") == material) {
                n++;
                largest[0] = Math.max(largest[0], Math.abs(a.get(i) - b.get(i)));
            }
        }
        return n;
    }

    @Test
    @DisplayName("Generation 0: 8 mutants of the tuned weights and 8 of the classic ones, material only, up to a pawn")
    void firstGeneration() {
        List<ParamVector> first = experiment.firstGeneration(SCHEMA, new Random(1));
        assertEquals(16, first.size());
        for (int m = 0; m < 16; m++) {
            ParamVector start = m < 8 ? SCHEMA.defaults() : ChessEvaluate.preset("classic");
            int[] largest = {0};
            assertEquals(0, changed(first.get(m), start, false, largest), "only material moves");
            assertTrue(changed(first.get(m), start, true, largest) > 0);
            assertTrue(largest[0] <= MaterialExperiment.MUTATION, "moved " + largest[0]);
        }
        assertEquals(10, MaterialExperiment.materialIndexes(SCHEMA).size());
    }

    @Test
    @DisplayName("Every pair plays 40 games: 30 at depth 3, 6 at depth 4, 2 at depth 5, 2 at depth 6")
    void schedule() {
        List<Pairing> pairings = experiment.pairings(List.of(SCHEMA.defaults(), SCHEMA.defaults(), SCHEMA.defaults()), new Random(1));
        assertEquals(3 * 20, pairings.size()); // 3 pairs, 20 openings each, both colours
        Map<Integer, Long> openingsAtDepth = pairings.stream().filter(p -> p.a() == 0 && p.b() == 1)
                .collect(Collectors.groupingBy(Pairing::depth, Collectors.counting()));
        assertEquals(Map.of(3, 15L, 4, 3L, 5, 1L, 6, 1L), openingsAtDepth);
    }

    /** Member i beats every higher-numbered member once; Stockfish scores as given. */
    private static Generation generation(int number, List<ParamVector> population, double[] stockfish) {
        List<GameRecord> games = new ArrayList<>();
        for (int a = 0; a < population.size(); a++) {
            for (int b = a + 1; b < population.size(); b++) {
                games.add(new GameRecord(Generation.name(a), Generation.name(b), "x", List.of(), Result.WHITE_WINS, "t", 0));
            }
        }
        List<GameRecord> sf = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            for (int k = 0; k < 10; k++) {
                Result r = k < Math.round(stockfish[i] * 10) ? Result.WHITE_WINS : Result.BLACK_WINS;
                sf.add(new GameRecord(Generation.name(i), "sf1320", "x", List.of(), r, "t", 0));
            }
        }
        return new Generation(number, population, games, sf, 1320);
    }

    @Test
    @DisplayName("The best three by fitness go on unchanged, then eight children bred from them")
    void nextGeneration() {
        List<ParamVector> population = experiment.firstGeneration(SCHEMA, new Random(2)).subList(0, 6);
        Generation g = generation(1, population, new double[]{0.5, 0.5, 0.5, 0.5, 0.5, 0.5});
        List<ParamVector> next = experiment.nextGeneration(g, new Random(3));
        assertEquals(MaterialExperiment.SURVIVORS + MaterialExperiment.CHILDREN, next.size());
        assertEquals(population.subList(0, 3), next.subList(0, 3));
        for (ParamVector child : next.subList(3, next.size())) {
            int[] largest = {0};
            assertEquals(0, changed(child, population.get(0), false, largest), "the rest comes from the best");
            for (int i : MaterialExperiment.materialIndexes(SCHEMA)) {
                int nearest = population.subList(0, 3).stream().mapToInt(p -> Math.abs(p.get(i) - child.get(i))).min().orElseThrow();
                assertTrue(nearest <= MaterialExperiment.CHILD_MUTATION, "a parent's value moved by " + nearest);
            }
        }
    }

    @Test
    @DisplayName("Beating Stockfish where the parents lost lifts a member above a better round-robin score")
    void stockfishBonus() {
        List<ParamVector> population = experiment.firstGeneration(SCHEMA, new Random(2)).subList(0, 7);
        // parents (0-2) score 20% against Stockfish; member 6 scores 100%, member 5 0%
        Generation g = generation(1, population, new double[]{0.2, 0.2, 0.2, 0.2, 0.2, 0.0, 1.0});
        assertTrue(g.score(5) > g.score(6)); // member 5 is ahead in the round robin, by one game in six
        assertEquals(0.2, MaterialExperiment.parentsStockfishScore(g), 1e-9);
        assertEquals(g.score(6) + MaterialExperiment.STOCKFISH_WEIGHT * 0.8, MaterialExperiment.fitness(g, 6), 1e-9);
        List<Integer> order = MaterialExperiment.byFitness(g);
        assertTrue(order.indexOf(6) < order.indexOf(5), order.toString());
    }

    @Test
    @DisplayName("Every fourth generation is born with the dramatic mutation")
    void dramatic() {
        List<ParamVector> population = experiment.firstGeneration(SCHEMA, new Random(2)).subList(0, 4);
        double[] even = {0.5, 0.5, 0.5, 0.5};
        int calm = largestChildStep(experiment.nextGeneration(generation(2, population, even), new Random(5)), population);
        int dramatic = largestChildStep(experiment.nextGeneration(generation(3, population, even), new Random(5)), population);
        assertTrue(calm <= MaterialExperiment.CHILD_MUTATION, "calm " + calm);
        assertTrue(dramatic > MaterialExperiment.CHILD_MUTATION && dramatic <= MaterialExperiment.DRAMATIC_MUTATION,
                "dramatic " + dramatic);
    }

    private static int largestChildStep(List<ParamVector> next, List<ParamVector> parents) {
        int largest = 0;
        for (ParamVector child : next.subList(MaterialExperiment.SURVIVORS, next.size())) {
            for (int i : MaterialExperiment.materialIndexes(SCHEMA)) {
                int c = child.get(i);
                largest = Math.max(largest, parents.subList(0, 3).stream().mapToInt(p -> Math.abs(p.get(i) - c)).min().orElseThrow());
            }
        }
        return largest;
    }
}
