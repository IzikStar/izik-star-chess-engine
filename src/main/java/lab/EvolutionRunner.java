package lab;

import ai.eval.Evaluators;
import ai.variant.Variant;
import ai.variant.Variants;
import ai.eval.ParamSchema;
import ai.eval.ParamVector;
import arena.FairyStockfish;
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
        /** A generation is about to play about {@code games} games. */
        default void generationStarted(int generation, int games) {}

        default void game(int generation, GameRecord game) {}

        default void generation(RunStore.GenerationRow row) {}

        Listener SILENT = new Listener() {};
    }

    private final RunStore store;
    private final Evolution evolution;
    private final RunSettings settings;
    private final Listener listener;
    private final Variant variant;
    private final ParamSchema schema;
    private final List<Opening> openings;

    private EvolutionRunner(RunStore store, Evolution evolution, RunSettings settings, Listener listener) {
        this.store = store;
        this.evolution = evolution;
        this.settings = settings;
        this.listener = listener;
        variant = settings.variant();
        schema = Evaluators.schema(variant);
        openings = settings.openings();
        Map<String, String> values = new java.util.LinkedHashMap<>(Evolution.resolve(evolution.options(),
                settings.algorithmOptions()));
        values.putIfAbsent("generations", String.valueOf(settings.generations()));
        evolution.configure(values);
    }

    /** Starts a new run in an empty store and plays it until done or {@code stop}. */
    public static void start(RunStore store, String name, Evolution evolution, RunSettings settings,
                             Listener listener, BooleanSupplier stop) {
        start(store, name, evolution, settings, listener, stop, () -> false);
    }

    /**
     * As {@link #start(RunStore, String, Evolution, RunSettings, Listener, BooleanSupplier)}; {@code stop}
     * is honoured between generations, {@code stopNow} between games, dropping the generation under way
     * (a resumed run plays it again).
     */
    public static void start(RunStore store, String name, Evolution evolution, RunSettings settings,
                             Listener listener, BooleanSupplier stop, BooleanSupplier stopNow) {
        RunSettings resolved = check(settings.withAlgorithmOptions(
                Evolution.resolve(evolution.options(), settings.algorithmOptions())));
        store.createRun(name, evolution.getClass().getName(), resolved);
        new EvolutionRunner(store, evolution, resolved, listener).play(stop, stopNow);
    }

    /**
     * {@code settings} if a run can play them: the variant exists, a variant other than chess starts
     * from random openings and has no Stockfish in it. Throws {@link IllegalArgumentException} otherwise.
     */
    public static RunSettings check(RunSettings settings) {
        Variant variant = settings.variant();
        if (!variant.equals(Variants.CHESS)) {
            if (settings.openingPlies() == 0) {
                throw new IllegalArgumentException(variant.name() + " has no opening book: give random opening moves");
            }
            if (settings.memberStockfishOpenings() > 0 || settings.yardsticks().stream().anyMatch(Players::isStockfish)) {
                throw new IllegalArgumentException("Stockfish plays chess only, not " + variant.name()
                        + " (Fairy-Stockfish, \"fsf\", plays the built-in variants)");
            }
        }
        if (settings.yardsticks().stream().anyMatch(Players::isFairyStockfish)) {
            if (!FairyStockfish.plays(variant)) {
                throw new IllegalArgumentException("Fairy-Stockfish does not know " + variant.name());
            }
            settings.yardsticks().stream().filter(Players::isFairyStockfish)
                    .forEach(y -> Players.label(y)); // a bad node count fails here, not mid-run
        }
        return settings;
    }

    /**
     * Continues a stopped run from its last finished generation; an unfinished generation is
     * played again from the start. {@code evolution} should be the algorithm that started it.
     */
    public static void resume(RunStore store, Evolution evolution, Listener listener, BooleanSupplier stop) {
        resume(store, evolution, listener, stop, () -> false);
    }

    /** As {@link #resume(RunStore, Evolution, Listener, BooleanSupplier)} with a {@code stopNow} between games. */
    public static void resume(RunStore store, Evolution evolution, Listener listener, BooleanSupplier stop,
                              BooleanSupplier stopNow) {
        RunStore.RunRow run = store.run().orElseThrow(() -> new IllegalStateException("no run in this file"));
        new EvolutionRunner(store, evolution, run.settings(), listener).play(stop, stopNow);
    }

    /** The evolution algorithms this program ships, for the Lab to offer. */
    public static List<String> algorithms() {
        return List.of("evolution.FromZero", "evolution.MaterialExperiment", "evolution.RandomMutationExample");
    }

    /** Creates the algorithm named {@code className} (it needs a public no-argument constructor). */
    public static Evolution algorithm(String className) {
        try {
            return (Evolution) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("cannot create evolution algorithm " + className, e);
        }
    }

    private void play(BooleanSupplier stop, BooleanSupplier stopNow) {
        if ((settings.yardsticks().stream().anyMatch(Players::isStockfish) || settings.memberStockfishOpenings() > 0)
                && StockfishLocator.find().isEmpty()) {
            throw new IllegalStateException("the yardsticks include Stockfish, but it is not installed;"
                    + " install it, download it from the game, or leave Stockfish out of the settings");
        }
        if (settings.yardsticks().stream().anyMatch(Players::isFairyStockfish) && !FairyStockfish.installed()) {
            throw new IllegalStateException("the yardsticks include Fairy-Stockfish, but it is not installed;"
                    + " put it in engine/ (see engine/README.md) or leave it out of the settings");
        }
        List<RunStore.GenerationRow> done = store.generations();
        int number = done.isEmpty() ? 0 : done.getLast().number() + 1;
        this.stopNow = stopNow;
        try {
            while (number < settings.generations() && !stop.getAsBoolean() && !stopNow.getAsBoolean()) {
                playGeneration(number);
                number++;
            }
        } catch (Tournament.Cancelled e) {
            // stopped between games: the generation under way is played again on resume
        }
    }

    private BooleanSupplier stopNow = () -> false;

    /** A {@code Random} for one generation's choices: the same on a resumed run. */
    private Random random(int generation, int purpose) {
        return new Random(settings.seed() * 1_000_003L + generation * 31L + purpose);
    }

    private void playGeneration(int number) {
        store.deleteGames(number); // left over if the run stopped inside this generation
        List<ParamVector> population = store.members(number, schema);
        if (population.isEmpty()) {
            if (number > 0) {
                throw new IllegalStateException("generation " + number + " has no stored members");
            }
            population = evolution.firstGeneration(schema, random(0, 0));
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
        List<Opening> suite = openings;
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
        int planned = fixtures.size() + 2 * population.size() * settings.memberStockfishOpenings();
        if (settings.yardstickEvery() > 0 && settings.yardstickOpenings() > 0
                && (number % settings.yardstickEvery() == 0 || number == settings.generations() - 1)) {
            planned += 2 * settings.yardstickOpenings() * settings.yardsticksAt(number).size();
        }
        listener.generationStarted(number, planned);
        List<GameRecord> games = Tournament.play(fixtures, tournamentSettings(), g -> listener.game(number, g), stopNow);
        for (int i = 0; i < games.size(); i++) {
            GameRecord g = games.get(i);
            store.saveGame(number, "population", Integer.parseInt(g.white()), Integer.parseInt(g.black()), g,
                    depths.get(i), null);
        }

        int stockfishLevel = 0;
        List<GameRecord> stockfishGames = List.of();
        if (settings.memberStockfishOpenings() > 0) {
            stockfishLevel = memberStockfishLevel(number);
            stockfishGames = membersAgainstStockfish(number, population, stockfishLevel);
        }
        Generation generation = new Generation(number, population, games, stockfishGames, stockfishLevel);
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

    /** The player name Stockfish plays under in the members' games against it: "sf" and its level. */
    static String stockfishName(int level) {
        return "sf" + level;
    }

    /**
     * The level every member plays Stockfish at in generation {@code number}: one up from the last
     * generation's if the members averaged above 70% against it, one down below 30%, else the
     * same; the lowest at first.
     */
    int memberStockfishLevel(int number) {
        if (number == 0) {
            return STOCKFISH_LEVELS[0];
        }
        List<GameRecord> last = store.games(number - 1, "stockfish");
        if (last.isEmpty()) {
            return STOCKFISH_LEVELS[0];
        }
        GameRecord first = last.getFirst();
        String sf = first.white().startsWith("sf") ? first.white() : first.black();
        int level = Integer.parseInt(sf.substring(2));
        double points = 0;
        for (GameRecord g : last) {
            points += 1 - g.scoreOf(sf);
        }
        return stepLevel(level, points / last.size());
    }

    /** One level up from {@code level} after a score above 70%, one down below 30%. */
    static int stepLevel(int level, double score) {
        int at = 0;
        while (at < STOCKFISH_LEVELS.length - 1 && STOCKFISH_LEVELS[at] < level) {
            at++;
        }
        if (score > 0.7) {
            at = Math.min(STOCKFISH_LEVELS.length - 1, at + 1);
        } else if (score < 0.3) {
            at = Math.max(0, at - 1);
        }
        return STOCKFISH_LEVELS[at];
    }

    /**
     * Every member against Stockfish held to {@code level}, over the same openings (they rotate
     * through the suite from generation to generation), each with both colours, at the run's depth.
     */
    private List<GameRecord> membersAgainstStockfish(int number, List<ParamVector> population, int level) {
        List<Opening> suite = openings;
        int count = settings.memberStockfishOpenings();
        List<Tournament.Fixture> fixtures = new ArrayList<>();
        long seed = (settings.seed() * 13 + number) * 1_000_003L + 500_009L;
        String spec = "sf:" + level;
        for (int i = 0; i < population.size(); i++) {
            Player member = player(Generation.name(i), population.get(i), settings.depth());
            for (int k = 0; k < count; k++) {
                Opening opening = suite.get((number * count + k) % suite.size());
                Player sf = Players.parse(spec, stockfishName(level), settings.depth(), settings.variety(),
                        HallOfFame.besides(store.file()).dir());
                fixtures.add(new Tournament.Fixture(member, sf, opening, seed++));
                fixtures.add(new Tournament.Fixture(sf, member, opening, seed++));
            }
        }
        List<GameRecord> games = Tournament.play(fixtures, tournamentSettings(), g -> listener.game(number, g), stopNow);
        for (GameRecord g : games) {
            boolean memberWhite = !g.white().startsWith("sf");
            store.saveGame(number, "stockfish", memberWhite ? Integer.parseInt(g.white()) : -1,
                    memberWhite ? -1 : Integer.parseInt(g.black()), g, settings.depth(), stockfishName(level));
        }
        return games;
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
            return stepLevel(level, last.get().fraction());
        }
        return STOCKFISH_LEVELS[0];
    }

    /**
     * The champion against each yardstick due this generation, over the first openings of the
     * suite, each with both colours, the generation's share of them at the deep depth.
     */
    private List<RunStore.YardstickResult> yardsticks(int number, int champion, ParamVector params) {
        List<Opening> openings = this.openings.subList(0, Math.min(settings.yardstickOpenings(), this.openings.size()));
        List<RunStore.YardstickResult> results = new ArrayList<>();
        Map<ParamVector, String> played = new HashMap<>();
        List<String> due = settings.yardsticksAt(number);
        for (int y = 0; y < due.size(); y++) {
            String spec = due.get(y);
            int level = spec.equals(RunSettings.STOCKFISH_AUTO) ? stockfishLevel(store.generations(), number) : 0;
            String playing = level > 0 ? "sf:" + level : spec;
            if (Players.hasWeights(playing)) {
                ParamVector weights = Players.params(playing, HallOfFame.besides(store.file()).dir(), schema);
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
                        HallOfFame.besides(store.file()).dir(), variant);
                fixtures.add(new Tournament.Fixture(candidate, opponent, openings.get(k), seed++));
                fixtures.add(new Tournament.Fixture(opponent, candidate, openings.get(k), seed++));
                depths.add(depth);
                depths.add(depth);
            }
            List<GameRecord> games = Tournament.play(fixtures, tournamentSettings(), g -> listener.game(number, g), stopNow);
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
        return Player.of(name, Evaluators.evaluator(variant, params), depth, settings.variety());
    }

    private Tournament.Settings tournamentSettings() {
        return new Tournament.Settings(settings.maxPlies(), settings.threads(), settings.seed(), variant);
    }

    private void check(List<ParamVector> population) {
        if (population == null || population.size() < 2) {
            throw new IllegalStateException("a generation needs at least two members");
        }
        for (ParamVector member : population) {
            if (member.schema() != schema) {
                throw new IllegalStateException("members must use the schema the runner hands firstGeneration");
            }
        }
    }
}
