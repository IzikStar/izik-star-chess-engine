package lab;

import ai.BitBoard.BitBoardEvaluate;
import ai.eval.ParamSchema;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.Opening;
import arena.Player;
import arena.Players;
import arena.Score;
import arena.Tournament;
import evolution.Evolution;
import evolution.Generation;
import evolution.Pairing;
import engine.StockfishLocator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/**
 * Runs an {@link Evolution} and records it in a {@link RunStore}: each generation it plays the
 * pairings the algorithm asks for (a share of them deeper, more in later generations; see
 * {@link RunSettings}), stores every game, picks the champion, every few generations plays the
 * champion against the yardsticks (players that do not move: the default weights, the classic
 * weights, Stockfish), keeps champions worth keeping in the {@link HallOfFame}, and asks the
 * algorithm for the next population. A run stops between generations when {@code stop} says
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
        if (settings.yardsticks().stream().anyMatch(Players::isStockfish) && StockfishLocator.find().isEmpty()) {
            throw new IllegalStateException("the yardsticks include Stockfish, but it is not installed;"
                    + " install it, download it from the game, or leave the sf: yardsticks out");
        }
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

        Map<Integer, List<Player>> players = new HashMap<>(); // by depth
        List<ParamVector> members = population;
        java.util.function.IntFunction<List<Player>> atDepth = depth -> players.computeIfAbsent(depth, d -> {
            List<Player> list = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                list.add(player(Generation.name(i), members.get(i), d));
            }
            return list;
        });
        List<Opening> suite = Opening.suite();
        List<Pairing> pairings = evolution.pairings(population, random(number, 1));
        boolean[] deep = deepUnits(number, (int) pairings.stream().filter(p -> p.depth() == 0).count()
                * settings.openingsPerPairing(), random(number, 3));
        List<Tournament.Fixture> fixtures = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        long seed = settings.seed() * 1_000_003L + number * 100_003L;
        int next = number * settings.openingsPerPairing(); // openings rotate through the suite
        int scheduled = 0;
        for (Pairing pairing : pairings) {
            for (int k = 0; k < settings.openingsPerPairing(); k++) {
                int depth = pairing.depth() > 0 ? pairing.depth()
                        : deep[scheduled++] ? settings.deepDepth() : settings.depth();
                List<Player> side = atDepth.apply(depth);
                Opening opening = suite.get(next++ % suite.size());
                fixtures.add(new Tournament.Fixture(side.get(pairing.a()), side.get(pairing.b()), opening, seed++));
                fixtures.add(new Tournament.Fixture(side.get(pairing.b()), side.get(pairing.a()), opening, seed++));
                depths.add(depth);
                depths.add(depth);
            }
        }
        List<GameRecord> games = Tournament.play(fixtures, tournamentSettings(), g -> listener.game(number, g));
        for (int i = 0; i < games.size(); i++) {
            GameRecord g = games.get(i);
            store.saveGame(number, "population", Integer.parseInt(g.white()), Integer.parseInt(g.black()), g,
                    depths.get(i), null);
        }

        Generation generation = new Generation(number, population, games);
        int champion = generation.champion();
        List<RunStore.YardstickResult> yardsticks = new ArrayList<>();
        boolean last = number == settings.generations() - 1;
        if (settings.yardstickEvery() > 0 && settings.yardstickOpenings() > 0
                && (number % settings.yardstickEvery() == 0 || last)) {
            yardsticks = yardsticks(number, champion, population.get(champion));
        }
        List<ParamVector> nextPopulation = null;
        if (!last) {
            nextPopulation = evolution.nextGeneration(generation, random(number, 2));
            check(nextPopulation);
        }
        Optional<Score> first = settings.yardsticks().isEmpty() ? Optional.empty()
                : RunStore.YardstickResult.combined(yardsticks, settings.yardsticks().getFirst());
        RunStore.GenerationRow row = new RunStore.GenerationRow(number, champion, generation.score(champion),
                games.size(), first, Instant.now().toString(), yardsticks);
        store.finishGeneration(row, nextPopulation);
        keep(row, last);
        listener.generation(row);
    }

    /** Keeps the champion in the hall of fame if it beat a yardstick or is the run's last champion. */
    private void keep(RunStore.GenerationRow row, boolean last) {
        List<String> reasons = HallOfFame.reasonsToKeep(row, last);
        if (reasons.isEmpty()) {
            return;
        }
        String runName = store.run().map(RunStore.RunRow::name).orElse("run");
        String name = HallOfFame.nameFor(runName, row.number(), row.champion());
        HallOfFame.besides(store.file()).add(HallOfFame.fromRun(store, store.file(), row.number(), row.champion(), name,
                String.join(", ", reasons)));
    }

    /**
     * Which of {@code count} units (a pairing's opening, or a yardstick's opening, both colours)
     * are played at the deep depth: the generation's share of them, picked at random.
     */
    private boolean[] deepUnits(int number, int count, Random random) {
        boolean[] deep = new boolean[count];
        int wanted = Math.round(count * settings.deepShare(number) / 100f);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        for (int i = 0; i < wanted; i++) {
            deep[order.get(i)] = true;
        }
        return deep;
    }

    /** UCI_Elo levels "sf:auto" moves between. */
    static final int[] STOCKFISH_LEVELS = {1320, 1500, 1700, 1900, 2100, 2300, 2500, 2700, 2900, 3190};

    /**
     * The level "sf:auto" plays at in generation {@code number}: one up from last time if the
     * champion scored above 70% then, one down below 30%, else the same; the lowest at first.
     */
    static int stockfishLevel(List<RunStore.GenerationRow> done, int number) {
        for (int i = done.size() - 1; i >= 0; i--) {
            RunStore.GenerationRow row = done.get(i);
            if (row.number() >= number) {
                continue;
            }
            Optional<Score> last = RunStore.YardstickResult.combined(row.yardsticks(), RunSettings.STOCKFISH_AUTO);
            if (last.isEmpty()) {
                continue;
            }
            int level = row.yardsticks().stream().filter(y -> y.opponent().equals(RunSettings.STOCKFISH_AUTO))
                    .mapToInt(RunStore.YardstickResult::level).findFirst().orElse(STOCKFISH_LEVELS[0]);
            int at = 0;
            while (at < STOCKFISH_LEVELS.length - 1 && STOCKFISH_LEVELS[at] < level) {
                at++;
            }
            double score = last.get().fraction();
            if (score > 0.7) {
                at = Math.min(STOCKFISH_LEVELS.length - 1, at + 1);
            } else if (score < 0.3) {
                at = Math.max(0, at - 1);
            }
            return STOCKFISH_LEVELS[at];
        }
        return STOCKFISH_LEVELS[0];
    }

    /**
     * The champion against each yardstick due this generation, over the first openings of the
     * suite, each with both colours, the generation's share of them at the deep depth.
     */
    private List<RunStore.YardstickResult> yardsticks(int number, int champion, ParamVector params) {
        List<Opening> openings = Opening.suite().subList(0, Math.min(settings.yardstickOpenings(), Opening.suite().size()));
        List<RunStore.YardstickResult> results = new ArrayList<>();
        Map<ParamVector, String> played = new HashMap<>();
        List<String> due = settings.yardsticksAt(number);
        for (int y = 0; y < due.size(); y++) {
            String spec = due.get(y);
            int level = spec.equals(RunSettings.STOCKFISH_AUTO) ? stockfishLevel(store.generations(), number) : 0;
            String playing = level > 0 ? "sf:" + level : spec;
            if (!Players.isStockfish(playing)) {
                ParamVector weights = Players.params(playing, HallOfFame.besides(store.file()).dir());
                String same = played.putIfAbsent(weights, spec);
                if (same != null) { // the same weights as an earlier yardstick: the same result
                    for (RunStore.YardstickResult r : List.copyOf(results)) {
                        if (r.opponent().equals(same)) {
                            results.add(new RunStore.YardstickResult(spec, 0, r.depth(), r.score()));
                        }
                    }
                    continue;
                }
            }
            boolean[] deep = deepUnits(number, openings.size(), random(number, 10 + y));
            List<Tournament.Fixture> fixtures = new ArrayList<>();
            List<Integer> depths = new ArrayList<>();
            long seed = (settings.seed() * 7 + number) * 1_000_003L + y * 10_007L;
            for (int k = 0; k < openings.size(); k++) {
                int depth = deep[k] ? settings.deepDepth() : settings.depth();
                Player candidate = player("champion", params, depth);
                Player opponent = Players.parse(playing, "yardstick", depth, settings.variety(),
                        HallOfFame.besides(store.file()).dir());
                fixtures.add(new Tournament.Fixture(candidate, opponent, openings.get(k), seed++));
                fixtures.add(new Tournament.Fixture(opponent, candidate, openings.get(k), seed++));
                depths.add(depth);
                depths.add(depth);
            }
            List<GameRecord> games = Tournament.play(fixtures, tournamentSettings(), g -> listener.game(number, g));
            Map<Integer, List<GameRecord>> byDepth = new TreeMap<>();
            for (int i = 0; i < games.size(); i++) {
                GameRecord g = games.get(i);
                boolean championWhite = g.white().equals("champion");
                store.saveGame(number, "yardstick", championWhite ? champion : -1, championWhite ? -1 : champion, g,
                        depths.get(i), Players.label(playing));
                byDepth.computeIfAbsent(depths.get(i), d -> new ArrayList<>()).add(g);
            }
            byDepth.forEach((depth, list) ->
                    results.add(new RunStore.YardstickResult(spec, level, depth, Score.of("champion", list))));
        }
        return results;
    }

    private Player player(String name, ParamVector params, int depth) {
        return Player.of(name, new BitBoardEvaluate(params), depth, settings.variety());
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
