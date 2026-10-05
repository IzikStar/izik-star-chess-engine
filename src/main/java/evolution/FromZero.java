package evolution;

import ai.eval.ParamSchema;
import ai.eval.ParamSpec;
import ai.eval.ParamVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A genetic algorithm that can start from nothing (the owner's decision D2: evolution from zero on
 * antichess), with every choice a setting the Lab shows:
 * <ul>
 *   <li><b>Start</b>: every weight 0, random weights, or the evaluation's defaults. Member 0 is the
 *       start itself; the others are scattered around it, so even an all-zero start has variety.</li>
 *   <li><b>Which weights evolve</b>: material only, material and mobility, or everything. The rest
 *       stay at the start.</li>
 *   <li><b>Selection</b>: the best few survive unchanged (elitism); a few fresh random members join
 *       (immigrants); the rest are children of two parents, each picked as the best of three
 *       random members (tournament selection).</li>
 *   <li><b>Children</b>: each evolving weight comes from one parent or the other (uniform
 *       crossover), or is the parents' average, then moves by a random normal step with a set
 *       chance. The step shrinks evenly (on a log scale) from the first generation to the last, so
 *       the search looks widely first and fine-tunes later.</li>
 * </ul>
 * Steps are in centipawns for a weight whose range is ±2000 (material) and in proportion to the
 * range for the others (a ±50 mobility weight moves 40 times less).
 */
public class FromZero implements Evolution {

    private int size = 12;
    private int survivors = 3;
    private int immigrants = 1;
    private String start = "zero";
    private int startSpread = 150;
    private String evolve = "material";
    private double stepFirst = 120;
    private double stepLast = 15;
    private double rate = 0.5;
    private String crossover = "uniform";
    private int generations = 20;

    @Override
    public List<Option> options() {
        return List.of(
                Option.number("population", "Population", "How many members each generation has.", 12, 4, 40),
                Option.number("survivors", "Survivors",
                        "The best this many go on to the next generation unchanged (elitism).", 3, 1, 20),
                Option.number("immigrants", "Immigrants",
                        "This many brand-new random members join every generation, so the population does not all"
                                + " become copies of one idea.", 1, 0, 10),
                Option.choice("start", "Start from",
                        "zero: every weight 0, the engine knows nothing but the rules. random: random weights."
                                + " defaults: the evaluation's own starting values.", "zero", List.of("zero", "random", "defaults")),
                Option.number("startSpread", "Start spread",
                        "How far the first members are scattered around the start, in centipawns of material.", 150, 0, 2000),
                Option.choice("evolve", "Evolve",
                        "Which weights change. material: what each piece is worth. +mobility: also what each square a"
                                + " piece can go to is worth. all: also the square tables (many more weights, slower to learn).",
                        "material", List.of("material", "material+mobility", "all")),
                Option.number("stepFirst", "First step",
                        "How far a mutation moves a material weight in the first generation (centipawns).", 120, 1, 1000),
                Option.number("stepLast", "Last step",
                        "... and in the last generation; the step shrinks evenly in between.", 15, 1, 1000),
                Option.number("rate", "Mutation chance",
                        "The chance that a child's weight is mutated at all (0.01 to 1).", 0.5, 0.01, 1),
                Option.choice("crossover", "Crossover",
                        "uniform: each weight from one parent or the other. average: the parents' average. none: copy"
                                + " the better parent.", "uniform", List.of("uniform", "average", "none")));
    }

    @Override
    public void configure(Map<String, String> v) {
        size = (int) Double.parseDouble(v.get("population"));
        survivors = Math.min(size - 1, (int) Double.parseDouble(v.get("survivors")));
        immigrants = Math.min(size - survivors, (int) Double.parseDouble(v.get("immigrants")));
        start = v.get("start");
        startSpread = (int) Double.parseDouble(v.get("startSpread"));
        evolve = v.get("evolve");
        stepFirst = Double.parseDouble(v.get("stepFirst"));
        stepLast = Double.parseDouble(v.get("stepLast"));
        rate = Double.parseDouble(v.get("rate"));
        crossover = v.get("crossover");
        generations = Integer.parseInt(v.getOrDefault("generations", "20"));
    }

    @Override
    public List<ParamVector> firstGeneration(ParamSchema schema, Random random) {
        ParamVector base = base(schema);
        List<ParamVector> population = new ArrayList<>();
        population.add(start.equals("random") ? scatter(base, random) : base);
        while (population.size() < size) {
            population.add(scatter(base, random));
        }
        return population;
    }

    @Override
    public List<ParamVector> nextGeneration(Generation generation, Random random) {
        List<ParamVector> members = generation.population();
        List<Integer> ranking = generation.ranking();
        ParamSchema schema = members.getFirst().schema();
        List<ParamVector> next = new ArrayList<>();
        for (int i = 0; i < survivors && i < ranking.size(); i++) {
            next.add(members.get(ranking.get(i)));
        }
        for (int i = 0; i < immigrants; i++) {
            next.add(scatter(base(schema), random));
        }
        double step = step(generation.number() + 1);
        while (next.size() < size) {
            int a = pick(ranking, random);
            int b = pick(ranking, random);
            for (int tries = 0; b == a && tries < 5; tries++) {
                b = pick(ranking, random);
            }
            next.add(mutate(child(members.get(a), members.get(b), generation, a, b, random), step, random));
        }
        return next;
    }

    /** The step of generation {@code number}: from {@link #stepFirst} to {@link #stepLast}, evenly on a log scale. */
    double step(int number) {
        if (generations <= 1) {
            return stepLast;
        }
        double t = Math.min(1, number / (double) (generations - 1));
        return stepFirst * Math.pow(stepLast / stepFirst, t);
    }

    /** The best of three random members: lower rank wins. */
    private static int pick(List<Integer> ranking, Random random) {
        int best = ranking.size();
        for (int i = 0; i < 3; i++) {
            best = Math.min(best, random.nextInt(ranking.size()));
        }
        return ranking.get(best);
    }

    private ParamVector child(ParamVector a, ParamVector b, Generation generation, int ia, int ib, Random random) {
        boolean aBetter = generation.score(ia) >= generation.score(ib);
        ParamVector better = aBetter ? a : b;
        if (crossover.equals("none")) {
            return better;
        }
        int[] values = better.toArray();
        for (int i = 0; i < values.length; i++) {
            if (!evolves(a.schema().spec(i))) {
                continue;
            }
            values[i] = crossover.equals("average") ? (int) Math.round((a.get(i) + b.get(i)) / 2.0)
                    : (random.nextBoolean() ? a : b).get(i);
        }
        return new ParamVector(a.schema(), values);
    }

    private ParamVector mutate(ParamVector p, double step, Random random) {
        int[] values = p.toArray();
        for (int i = 0; i < values.length; i++) {
            ParamSpec spec = p.schema().spec(i);
            if (evolves(spec) && random.nextDouble() < rate) {
                values[i] = spec.clamp((int) Math.round(values[i] + random.nextGaussian() * scaled(spec, step)));
            }
        }
        return new ParamVector(p.schema(), values);
    }

    /** {@code base} with every evolving weight moved by a normal step of {@link #startSpread}. */
    private ParamVector scatter(ParamVector base, Random random) {
        int[] values = base.toArray();
        for (int i = 0; i < values.length; i++) {
            ParamSpec spec = base.schema().spec(i);
            if (evolves(spec)) {
                values[i] = spec.clamp((int) Math.round(values[i] + random.nextGaussian() * scaled(spec, startSpread)));
            }
        }
        return new ParamVector(base.schema(), values);
    }

    private ParamVector base(ParamSchema schema) {
        int[] values = schema.defaults().toArray();
        if (!start.equals("defaults")) {
            for (int i = 0; i < values.length; i++) {
                if (evolves(schema.spec(i))) {
                    values[i] = schema.spec(i).clamp(0);
                }
            }
        }
        return new ParamVector(schema, values);
    }

    /** A step for a weight with a ±2000 range, in proportion to {@code spec}'s range. */
    private static double scaled(ParamSpec spec, double step) {
        return Math.max(1, step * (spec.max() - spec.min()) / 4000.0);
    }

    boolean evolves(ParamSpec spec) {
        String group = spec.group();
        return switch (evolve) {
            case "all" -> true;
            case "material+mobility" -> group.equals("material") || group.equals("mobility");
            default -> group.equals("material");
        };
    }
}
