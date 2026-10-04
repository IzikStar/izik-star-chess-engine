package lab;

import arena.GameRecord;
import arena.Score;
import evolution.Evolution;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Command line for evolution runs:
 *
 * <pre>
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/first.db --algorithm evolution.RandomMutationExample [options]
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli resume runs/first.db [--algorithm CLASS]
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli show runs/first.db
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli export runs/first.db positions.csv
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli champion runs/first.db 19 champion.json
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli selfplay positions.csv [--games N --nodes N --threads N --seed N]
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli tune positions.csv tuned.json [--from classic --iterations N]
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli pgn runs/first.db games.pgn [--generation N --member M]
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli keep runs/first.db GENERATION MEMBER NAME [--note TEXT]
 * java -cp target/izikstar-chess-3.1.0.jar lab.Cli fame [runs/hall-of-fame]
 *
 * run options (defaults in RunSettings.defaults()):
 *   --name TEXT --generations N --depth N --openings-per-pairing N --variety N --max-plies N
 *   --threads N --seed N --yardstick-every N --yardstick-openings N
 *   --yardsticks default,classic,sf:auto   who the champion is measured against (see RunSettings)
 *   --stockfish-from N   Stockfish yardsticks from this generation on
 *   --deep-depth N --deep-share 10-40      percent of games at the deep depth, first-last generation
 *   --member-stockfish-openings N          every member plays Stockfish over N openings (both colours)
 * </pre>
 *
 * Ctrl+C stops a run after the generation in progress; {@code resume} continues it.
 */
public final class Cli {

    private Cli() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("fame")) {
            fame(arena.Players.HALL_OF_FAME);
            return;
        }
        if (args.length < 2) {
            usage();
        }
        Path file = Path.of(args[1]);
        Map<String, String> options = options(args, switch (args[0]) {
            case "export", "tune", "pgn" -> 3;
            case "champion" -> 4;
            case "keep" -> 5;
            default -> 2;
        });
        switch (args[0]) {
            case "run" -> run(file, options);
            case "resume" -> resume(file, options);
            case "show" -> show(file);
            case "selfplay" -> selfPlay(file, options);
            case "tune" -> {
                if (args.length < 3) {
                    usage();
                }
                tune(file, Path.of(args[2]), options);
            }
            case "fame" -> fame(file);
            case "pgn" -> {
                if (args.length < 3) {
                    usage();
                }
                pgn(file, Path.of(args[2]), options);
            }
            case "keep" -> {
                if (args.length < 5) {
                    usage();
                }
                keep(file, Integer.parseInt(args[2]), Integer.parseInt(args[3]), args[4], options);
            }
            case "export" -> {
                if (args.length < 3) {
                    usage();
                }
                export(file, Path.of(args[2]));
            }
            case "champion" -> {
                if (args.length < 4) {
                    usage();
                }
                champion(file, Integer.parseInt(args[2]), Path.of(args[3]));
            }
            default -> usage();
        }
    }

    /** Writes a generation's champion as a parameter file, e.g. for {@code arena.Cli match}. */
    private static void champion(Path file, int generation, Path out) throws IOException {
        try (RunStore store = RunStore.open(file)) {
            RunStore.GenerationRow row = store.generations().stream().filter(r -> r.number() == generation)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("generation " + generation + " is not finished"));
            Files.writeString(out, store.members(generation, ai.BitBoard.BitBoardEvaluate.SCHEMA).get(row.champion()).toJson());
            System.out.println("generation " + generation + "'s champion (#" + row.champion() + ") written to " + out);
        }
    }

    private static void run(Path file, Map<String, String> o) {
        RunSettings d = RunSettings.defaults();
        RunSettings settings = new RunSettings(
                integer(o, "generations", d.generations()), integer(o, "depth", d.depth()),
                integer(o, "openings-per-pairing", d.openingsPerPairing()), integer(o, "variety", d.variety()),
                integer(o, "max-plies", d.maxPlies()), integer(o, "threads", d.threads()),
                Long.parseLong(o.getOrDefault("seed", String.valueOf(d.seed()))),
                integer(o, "yardstick-every", d.yardstickEvery()), integer(o, "yardstick-openings", d.yardstickOpenings()),
                o.containsKey("yardsticks") ? List.of(o.get("yardsticks").split(",")) : d.yardsticks(),
                integer(o, "stockfish-from", d.stockfishFrom()), integer(o, "deep-depth", d.deepDepth()),
                share(o, 0, d.deepShareFirst()), share(o, 1, d.deepShareLast()),
                integer(o, "member-stockfish-openings", d.memberStockfishOpenings()));
        Evolution evolution = EvolutionRunner.algorithm(o.getOrDefault("algorithm", "evolution.RandomMutationExample"));
        String name = o.getOrDefault("name", file.getFileName().toString().replaceFirst("\\.db$", ""));
        try (RunStore store = RunStore.open(file)) {
            EvolutionRunner.start(store, name, evolution, settings, printer(), stopOnCtrlC());
            show(store);
        }
    }

    private static void resume(Path file, Map<String, String> o) {
        try (RunStore store = RunStore.open(file)) {
            String algorithm = o.getOrDefault("algorithm",
                    store.run().orElseThrow(() -> new IllegalStateException("no run in " + file)).algorithm());
            EvolutionRunner.resume(store, EvolutionRunner.algorithm(algorithm), printer(), stopOnCtrlC());
            show(store);
        }
    }

    private static void show(Path file) {
        try (RunStore store = RunStore.open(file)) {
            show(store);
        }
    }

    private static void show(RunStore store) {
        store.run().ifPresent(r -> System.out.printf("%s (%s), started %s%n%s%n", r.name(), r.algorithm(),
                r.startedAt(), r.settings()));
        for (RunStore.GenerationRow row : store.generations()) {
            System.out.printf("generation %3d: champion #%d scored %.0f%% in %d games%n", row.number(),
                    row.champion(), 100 * row.championScore(), row.games());
            yardsticks(row);
        }
    }

    /** Each yardstick's score, all depths together and then per depth. */
    private static void yardsticks(RunStore.GenerationRow row) {
        List<String> seen = new ArrayList<>();
        for (RunStore.YardstickResult y : row.yardsticks()) {
            if (seen.contains(y.opponent())) {
                continue;
            }
            seen.add(y.opponent());
            List<RunStore.YardstickResult> mine = row.yardsticks().stream()
                    .filter(r -> r.opponent().equals(y.opponent())).toList();
            StringBuilder depths = new StringBuilder();
            if (mine.size() > 1) {
                mine.forEach(r -> depths.append(String.format("; depth %d: %.0f%%", r.depth(), 100 * r.score().fraction())));
            }
            System.out.printf("    vs %-10s %s%s%n", y.label(),
                    RunStore.YardstickResult.combined(mine, y.opponent()).orElseThrow(), depths);
        }
    }

    private static void export(Path file, Path out) throws IOException {
        try (RunStore store = RunStore.open(file); Writer w = Files.newBufferedWriter(out)) {
            int lines = TrainingExport.write(store.allGames(), w);
            System.out.println(lines + " positions written to " + out);
        }
    }

    /** Stockfish against itself: quiet positions with results, for {@code tune}. */
    private static void selfPlay(Path out, Map<String, String> o) throws IOException {
        int games = integer(o, "games", 4000);
        long start = System.nanoTime();
        try (Writer w = Files.newBufferedWriter(out)) {
            int lines = Texel.selfPlay(games, integer(o, "nodes", 5000), integer(o, "threads",
                            RunSettings.defaults().threads()), Long.parseLong(o.getOrDefault("seed", "1")), w,
                    System.out::println);
            System.out.printf("%d positions from %d games written to %s in %.0f s%n", lines, games, out,
                    (System.nanoTime() - start) / 1e9);
        }
    }

    /** Texel tuning: fits the weights to the positions' results. */
    private static void tune(Path positions, Path out, Map<String, String> o) throws IOException {
        List<Texel.Sample> samples = Texel.load(positions);
        Texel.Result r = Texel.tune(samples, arena.Players.params(o.getOrDefault("from", "classic")),
                integer(o, "iterations", 1000), Double.parseDouble(o.getOrDefault("regularization", "1e-7")),
                System.out::println);
        Files.writeString(out, r.params().toJson());
        System.out.printf("error %.5f -> %.5f, held out %.5f -> %.5f (k %.3f, %d positions); written to %s%n",
                r.startError(), r.endError(), r.holdOutStart(), r.holdOutEnd(), r.k(), r.samples(), out);
    }

    /** A run's games as PGN: all, one generation's, or one member's. */
    private static void pgn(Path file, Path out, Map<String, String> o) throws IOException {
        try (RunStore store = RunStore.open(file); Writer w = Files.newBufferedWriter(out)) {
            int games = RunPgn.write(store, integer(o, "generation", -1), integer(o, "member", -1), w);
            System.out.println(games + " games written to " + out);
        }
    }

    /** Keeps a member in the hall of fame beside the run, with its weights, results and games. */
    private static void keep(Path file, int generation, int member, String name, Map<String, String> o) {
        try (RunStore store = RunStore.open(file)) {
            HallOfFame hall = HallOfFame.besides(file);
            hall.add(HallOfFame.fromRun(store, file, generation, member, name, o.getOrDefault("note", "kept by hand")));
            System.out.println("kept as hof:" + name + " in " + hall.dir());
        }
    }

    /** Lists the hall of fame. */
    private static void fame(Path dir) {
        List<HallOfFame.Entry> entries = new HallOfFame(dir).list();
        if (entries.isEmpty()) {
            System.out.println("nothing in " + dir + " yet");
        }
        for (HallOfFame.Entry e : entries) {
            System.out.printf("hof:%s  (%s, generation %d, #%d) %s%n", e.name(), e.runName(), e.generation(), e.member(),
                    e.reason());
            e.yardsticks().forEach(y -> System.out.println("    " + y));
        }
    }

    private static EvolutionRunner.Listener printer() {
        return new EvolutionRunner.Listener() {
            @Override
            public void game(int generation, GameRecord g) {
                System.out.printf("gen %d  %-28s %s - %s: %s (%s, %d plies)%n", generation, g.opening(), g.white(),
                        g.black(), g.result(), g.reason(), g.plies());
            }

            @Override
            public void generation(RunStore.GenerationRow row) {
                System.out.printf("== generation %d done: champion #%d, %.0f%%%n", row.number(), row.champion(),
                        100 * row.championScore());
                yardsticks(row);
            }
        };
    }

    private static java.util.function.BooleanSupplier stopOnCtrlC() {
        AtomicBoolean stop = new AtomicBoolean();
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (main.isAlive() && !stop.getAndSet(true)) { // not on a normal exit
                System.out.println("stopping after this generation (closing the window quits at once; resume replays it)");
            }
            try {
                main.join();
            } catch (InterruptedException ignored) {
                // quitting anyway
            }
        }));
        return stop::get;
    }

    private static Map<String, String> options(String[] args, int from) {
        Map<String, String> options = new TreeMap<>();
        for (int i = from; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("expected --option value, got " + args[i]);
            }
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    /** {@code --deep-share 10-40}: percent of deep games in the first and the last generation. */
    private static int share(Map<String, String> options, int which, int fallback) {
        if (!options.containsKey("deep-share")) {
            return fallback;
        }
        String[] parts = options.get("deep-share").split("-");
        return Integer.parseInt(parts[Math.min(which, parts.length - 1)]);
    }

    private static int integer(Map<String, String> options, String name, int fallback) {
        return Integer.parseInt(options.getOrDefault(name, String.valueOf(fallback)));
    }

    private static void usage() {
        System.err.println("usage: lab.Cli run FILE --algorithm CLASS [options] | resume FILE | show FILE | export FILE OUT.csv"
                + " | champion FILE GENERATION OUT.json | pgn FILE OUT.pgn | keep FILE GENERATION MEMBER NAME | fame DIR"
                + " | selfplay OUT.csv | tune POSITIONS.csv OUT.json");
        System.exit(2);
    }
}
