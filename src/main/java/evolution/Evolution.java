package evolution;

import ai.eval.ParamSchema;
import ai.eval.ParamVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An evolution algorithm: the part of Phase 5 that is the owner's to write
 * (docs/evolution-guide.md). The {@code lab.EvolutionRunner} does everything around it: it plays
 * the games, records every member, game and result in the run's database, measures the champion
 * against the default weights, and can stop and resume.
 *
 * <p>Each generation the runner asks {@link #pairings} who plays whom, plays those games (every
 * pairing over a few openings, once with each colour), and hands the population with its games
 * to {@link #nextGeneration}. Every call gets a {@code Random} seeded from the run's seed and the
 * generation number: use only it for chance, and a run can be repeated and resumed exactly.
 *
 * <p>The class needs a public constructor without arguments, so the runner can create it by name.
 */
public interface Evolution {

    /**
     * The first population. Its size is up to you. {@code schema.defaults()} is where tuning starts;
     * {@code ChessEvaluate.preset("classic")} is the hand-written weights the game plays with.
     */
    List<ParamVector> firstGeneration(ParamSchema schema, Random random);

    /** Who plays whom this generation. The default is a round robin: everyone meets everyone once. */
    default List<Pairing> pairings(List<ParamVector> population, Random random) {
        List<Pairing> pairings = new ArrayList<>();
        for (int a = 0; a < population.size(); a++) {
            for (int b = a + 1; b < population.size(); b++) {
                pairings.add(new Pairing(a, b));
            }
        }
        return pairings;
    }

    /** The next population, from this generation's members and games. */
    List<ParamVector> nextGeneration(Generation generation, Random random);

    /** The settings this algorithm offers the Lab and the command line; none by default. */
    default List<Option> options() {
        return List.of();
    }

    /**
     * The values chosen for {@link #options()} (each checked, every option present: a missing one
     * has its default), before the first call of a run. The runner also passes "generations", the
     * run's length, whether or not it is an option.
     */
    default void configure(java.util.Map<String, String> values) {}

    /** {@code chosen} checked against {@code options}, with the defaults filled in. */
    static java.util.Map<String, String> resolve(List<Option> options, java.util.Map<String, String> chosen) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Option o : options) {
            String v = chosen.get(o.key());
            out.put(o.key(), v == null || v.isBlank() ? o.defaultValue() : o.check(v));
        }
        for (String key : chosen.keySet()) {
            if (options.stream().noneMatch(o -> o.key().equals(key))) {
                throw new IllegalArgumentException("unknown setting " + key);
            }
        }
        return out;
    }
}
