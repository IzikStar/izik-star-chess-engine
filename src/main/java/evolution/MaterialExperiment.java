package evolution;

import ai.BitBoard.BitBoardEvaluate;
import ai.eval.ParamSchema;
import ai.eval.ParamVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * The first evolution experiment (2026-10-04, the owner's design): only the material weights
 * ({@code material.*}, 10 numbers) evolve; everything else stays as the parent had it.
 *
 * <ul>
 *   <li>Generation 0: 8 mutants of the tuned weights and 8 of the classic ones, each material
 *       weight moved by a random whole number of centipawns up to {@link #MUTATION} either way.</li>
 *   <li>Everyone plays everyone, 40 games a pair: most at depth 3, a few deeper
 *       ({@link #SCHEDULE}).</li>
 *   <li>Every member also plays Stockfish (the run's {@code --member-stockfish-openings}). A member
 *       that does better against Stockfish than its parents (the survivors, members 0 to 2) did
 *       that same generation gets a bonus; one that does worse, a penalty
 *       ({@link #STOCKFISH_WEIGHT}). In generation 0 the comparison is with the average.</li>
 *   <li>The best {@link #SURVIVORS} by that fitness go on unchanged, followed by
 *       {@link #CHILDREN} children: each material weight is taken from one of the survivors, the
 *       best with probability ½, the second ⅓, the third ⅙, then moved by up to
 *       {@link #CHILD_MUTATION}, or {@link #DRAMATIC_MUTATION} every {@link #DRAMATIC_EVERY}th
 *       generation.</li>
 * </ul>
 */
public class MaterialExperiment implements Evolution {

    /** Each starting set of weights gets this many mutants in generation 0. */
    static final int MUTANTS_PER_START = 8;
    static final int MUTATION = 100;
    static final int SURVIVORS = 3;
    static final int CHILDREN = 8;
    static final int CHILD_MUTATION = 20;
    static final int DRAMATIC_MUTATION = 200;
    /** Generations 4, 8, 12... are born with the dramatic mutation. */
    static final int DRAMATIC_EVERY = 4;
    /** Fitness = score in the round robin + this × (score against Stockfish − the parents'). */
    static final double STOCKFISH_WEIGHT = 0.25;
    /** The chance each survivor, best first, passes on a weight. */
    private static final double[] INHERIT = {1 / 2.0, 1 / 3.0, 1 / 6.0};

    /** Games a pair plays at each depth, as {depth, openings}; each opening with both colours. 40 games. */
    static final int[][] SCHEDULE = {{3, 15}, {4, 3}, {5, 1}, {6, 1}};

    @Override
    public List<ParamVector> firstGeneration(ParamSchema schema, Random random) {
        List<ParamVector> population = new ArrayList<>();
        for (ParamVector start : List.of(schema.defaults(), BitBoardEvaluate.preset("classic"))) {
            for (int i = 0; i < MUTANTS_PER_START; i++) {
                population.add(mutate(start, MUTATION, random));
            }
        }
        return population;
    }

    @Override
    public List<Pairing> pairings(List<ParamVector> population, Random random) {
        List<Pairing> pairings = new ArrayList<>();
        for (int a = 0; a < population.size(); a++) {
            for (int b = a + 1; b < population.size(); b++) {
                for (int[] depthOpenings : SCHEDULE) {
                    for (int k = 0; k < depthOpenings[1]; k++) {
                        pairings.add(new Pairing(a, b, depthOpenings[0]));
                    }
                }
            }
        }
        return pairings;
    }

    @Override
    public List<ParamVector> nextGeneration(Generation generation, Random random) {
        List<Integer> best = byFitness(generation);
        List<ParamVector> parents = best.subList(0, SURVIVORS).stream().map(generation.population()::get).toList();
        int born = generation.number() + 1;
        int mutation = born % DRAMATIC_EVERY == 0 ? DRAMATIC_MUTATION : CHILD_MUTATION;
        List<ParamVector> next = new ArrayList<>(parents);
        for (int c = 0; c < CHILDREN; c++) {
            next.add(mutate(crossbreed(parents, random), mutation, random));
        }
        return next;
    }

    /** Member indexes from the fittest to the least fit. */
    static List<Integer> byFitness(Generation generation) {
        return IntStream.range(0, generation.size()).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> -fitness(generation, i)).thenComparing(i -> i))
                .toList();
    }

    /** The round-robin score, plus or minus how member {@code i} did against Stockfish compared with its parents. */
    static double fitness(Generation generation, int i) {
        double own = generation.stockfishScore(i);
        double parents = parentsStockfishScore(generation);
        double bonus = Double.isNaN(own) || Double.isNaN(parents) ? 0 : STOCKFISH_WEIGHT * (own - parents);
        return generation.score(i) + bonus;
    }

    /** The survivors' (members 0 to 2) average score against Stockfish; in generation 0 everyone's. */
    static double parentsStockfishScore(Generation generation) {
        int n = generation.number() == 0 ? generation.size() : Math.min(SURVIVORS, generation.size());
        double sum = 0;
        int counted = 0;
        for (int i = 0; i < n; i++) {
            double s = generation.stockfishScore(i);
            if (!Double.isNaN(s)) {
                sum += s;
                counted++;
            }
        }
        return counted == 0 ? Double.NaN : sum / counted;
    }

    /** Each material weight from one parent (the best most often); the other weights from the best. */
    private static ParamVector crossbreed(List<ParamVector> parents, Random random) {
        int[] values = parents.getFirst().toArray();
        for (int i : materialIndexes(parents.getFirst().schema())) {
            double r = random.nextDouble();
            int from = 0;
            while (from < parents.size() - 1 && r >= INHERIT[from]) {
                r -= INHERIT[from];
                from++;
            }
            values[i] = parents.get(from).get(i);
        }
        return new ParamVector(parents.getFirst().schema(), values);
    }

    /** {@code start} with each material weight moved by a random whole number from −{@code size} to +{@code size}. */
    static ParamVector mutate(ParamVector start, int size, Random random) {
        int[] values = start.toArray();
        for (int i : materialIndexes(start.schema())) {
            values[i] = start.schema().spec(i).clamp(values[i] + random.nextInt(2 * size + 1) - size);
        }
        return new ParamVector(start.schema(), values);
    }

    static List<Integer> materialIndexes(ParamSchema schema) {
        return IntStream.range(0, schema.size()).filter(i -> schema.spec(i).group().equals("material")).boxed().toList();
    }
}
