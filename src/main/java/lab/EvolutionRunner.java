package lab;

import ai.BitBoard.BitBoardEvaluate;
import ai.eval.ParamSchema;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.Opening;
import arena.Player;
import arena.Score;
import arena.Tournament;
import evolution.Evolution;
import evolution.Generation;
import evolution.Pairing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Runs an {@link Evolution} and records it in a {@link RunStore}: each generation it plays the
 * pairings the algorithm asks for, stores every game, picks the champion, every few generations
 * plays the champion against the default weights (the yardstick that does not move), and asks
 * the algorithm for the next population. A run stops between generations when {@code stop} says
 * so and continues later with {@link #resume}.
 */
public final class EvolutionRunner {

    /** Hears what a run does, for progress output. Called from the playing threads too. */
    public interface Listener {
        default void game(int generation, GameRecord game) {}

        default void generation(RunStore.GenerationRow row) {}

        Listener SILENT = new Listener() {};
    }

    private static final ParamSchema SCHEMA = BitBoardEvaluate.SCHEMA;

    private final RunStore store;
    private final Evolution evolution;
    private final RunSettings settings;
    private final Listener listener;

    private EvolutionRunner(RunStore store, Evolution evolution, RunSettings settings, Listener listener) {
        this.store = store;
        this.evolution = evolution;
        this.settings = settings;
        this.listener = listener;
    }

    /** Starts a new run in an empty store and plays it until done or {@code stop}. */
    public static void start(RunStore store, String name, Evolution evolution, RunSettings settings,
                             Listener listener, BooleanSupplier stop) {
        store.createRun(name, evolution.getClass().getName(), settings);
        new EvolutionRunner(store, evolution, settings, listener).play(stop);
    }

    /**
     * Continues a stopped run from its last finished generation; an unfinished generation is
     * played again from the start. {@code evolution} should be the algorithm that started it.
     */
    public static void resume(RunStore store, Evolution evolution, Listener listener, BooleanSupplier stop) {
        RunStore.RunRow run = store.run().orElseThrow(() -> new IllegalStateException("no run in this file"));
        new EvolutionRunner(store, evolution, run.settings(), listener).play(stop);
    }

    /** Creates the algorithm named {@code className} (it needs a public no-argument constructor). */
    public static Evolution algorithm(String className) {
        try {
            return (Evolution) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("cannot create evolution algorithm " + className, e);
        }
    }

    private void play(BooleanSupplier stop) {
        List<RunStore.GenerationRow> done = store.generations();
        int number = done.isEmpty() ? 0 : done.getLast().number() + 1;
        while (number < settings.generations() && !stop.getAsBoolean()) {
            playGeneration(number);
            number++;
        }
    }

    /** A {@code Random} for one generation's choices: the same on a resumed run. */
    private Random random(int generation, int purpose) {
        return new Random(settings.seed() * 1_000_003L + generation * 31L + purpose);
    }

    private void playGeneration(int number) {
        store.deleteGames(number); // left over if the run stopped inside this generation
        List<ParamVector> population = store.members(number, SCHEMA);
        if (population.isEmpty()) {
            if (number > 0) {
                throw new IllegalStateException("generation " + number + " has no stored members");
            }
            population = evolution.firstGeneration(SCHEMA, random(0, 0));
            check(population);
            store.saveMembers(0, population);
        }

        List<Player> players = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            players.add(player(Generation.name(i), population.get(i)));
        }
        List<Opening> suite = Opening.suite();
        List<Pairing> pairings = evolution.pairings(population, random(number, 1));
        List<Tournament.Fixture> fixtures = new ArrayList<>();
        long seed = settings.seed() * 1_000_003L + number * 100_003L;
        int next = number * settings.openingsPerPairing(); // openings rotate through the suite
        for (Pairing pairing : pairings) {
            for (int k = 0; k < settings.openingsPerPairing(); k++) {
                Opening opening = suite.get(next++ % suite.size());
                fixtures.add(new Tournament.Fixture(players.get(pairing.a()), players.get(pairing.b()), opening, seed++));
                fixtures.add(new Tournament.Fixture(players.get(pairing.b()), players.get(pairing.a()), opening, seed++));
            }
        }
        List<GameRecord> games = Tournament.play(fixtures, tournamentSettings(), g -> listener.game(number, g));
        for (GameRecord g : games) {
            store.saveGame(number, "population", Integer.parseInt(g.white()), Integer.parseInt(g.black()), g);
        }

        Generation generation = new Generation(number, population, games);
        int champion = generation.champion();
        Score yardstick = null;
        boolean last = number == settings.generations() - 1;
        if (settings.yardstickEvery() > 0 && settings.yardstickOpenings() > 0
                && (number % settings.yardstickEvery() == 0 || last)) {
            yardstick = yardstick(number, champion, population.get(champion));
        }
        List<ParamVector> nextPopulation = null;
        if (!last) {
            nextPopulation = evolution.nextGeneration(generation, random(number, 2));
            check(nextPopulation);
        }
        RunStore.GenerationRow row = new RunStore.GenerationRow(number, champion, generation.score(champion),
                games.size(), java.util.Optional.ofNullable(yardstick), Instant.now().toString());
        store.finishGeneration(row, nextPopulation);
        listener.generation(row);
    }

    /** The champion against the default weights, over the first openings of the suite. */
    private Score yardstick(int number, int champion, ParamVector params) {
        Player candidate = player("champion", params);
        Player yardstick = Player.yardstick(settings.depth(), settings.variety());
        List<Opening> openings = Opening.suite().subList(0, Math.min(settings.yardstickOpenings(), Opening.suite().size()));
        Tournament.Settings tournament = new Tournament.Settings(settings.maxPlies(), settings.threads(),
                settings.seed() * 7 + number);
        List<GameRecord> games = Tournament.match(candidate, yardstick, openings, tournament, g -> listener.game(number, g));
        for (GameRecord g : games) {
            boolean championWhite = g.white().equals("champion");
            store.saveGame(number, "yardstick", championWhite ? champion : -1, championWhite ? -1 : champion, g);
        }
        return Score.of("champion", games);
    }

    private Player player(String name, ParamVector params) {
        return Player.of(name, new BitBoardEvaluate(params), settings.depth(), settings.variety());
    }

    private Tournament.Settings tournamentSettings() {
        return new Tournament.Settings(settings.maxPlies(), settings.threads(), settings.seed());
    }

    private static void check(List<ParamVector> population) {
        if (population == null || population.size() < 2) {
            throw new IllegalStateException("a generation needs at least two members");
        }
        for (ParamVector member : population) {
            if (member.schema() != SCHEMA) {
                throw new IllegalStateException("members must use BitBoardEvaluate.SCHEMA");
            }
        }
    }
}
