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
     * {@code BitBoardEvaluate.preset("classic")} is the hand-written weights the game plays with.
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
}
