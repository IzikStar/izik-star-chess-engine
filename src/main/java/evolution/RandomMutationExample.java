package evolution;

import ai.eval.ParamSchema;
import ai.eval.ParamSpec;
import ai.eval.ParamVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A deliberately naive {@link Evolution}, there to prove the plumbing works and to show the API:
 * start from the default weights and mutated copies of them, keep the better half each
 * generation, and refill with mutated copies of the survivors. It is not the real algorithm; read
 * it, change it, or delete it.
 */
public class RandomMutationExample implements Evolution {

    private final int populationSize;
    /** Chance that a mutation touches each parameter. */
    private final double rate;
    /** A touched parameter moves by a normal step of this fraction of its range. */
    private final double step;

    public RandomMutationExample() {
        this(8, 0.05, 0.01);
    }

    public RandomMutationExample(int populationSize, double rate, double step) {
        if (populationSize < 2) {
            throw new IllegalArgumentException("a population needs at least two members");
        }
        this.populationSize = populationSize;
        this.rate = rate;
        this.step = step;
    }

    @Override
    public List<ParamVector> firstGeneration(ParamSchema schema, Random random) {
        List<ParamVector> population = new ArrayList<>();
        population.add(schema.defaults());
        while (population.size() < populationSize) {
            population.add(mutate(schema.defaults(), random));
        }
        return population;
    }

    @Override
    public List<ParamVector> nextGeneration(Generation generation, Random random) {
        List<Integer> ranking = generation.ranking();
        List<ParamVector> next = new ArrayList<>();
        for (int i = 0; i < populationSize / 2; i++) {
            next.add(generation.population().get(ranking.get(i)));
        }
        int survivors = next.size();
        while (next.size() < populationSize) {
            next.add(mutate(next.get(random.nextInt(survivors)), random));
        }
        return next;
    }

    private ParamVector mutate(ParamVector parent, Random random) {
        int[] values = parent.toArray();
        for (int i = 0; i < values.length; i++) {
            if (random.nextDouble() < rate) {
                ParamSpec spec = parent.schema().spec(i);
                double range = spec.max() - spec.min();
                values[i] = spec.clamp((int) Math.round(values[i] + random.nextGaussian() * step * range));
            }
        }
        return new ParamVector(parent.schema(), values);
    }
}
