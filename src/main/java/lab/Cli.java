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
 *
 * run options (defaults in RunSettings.defaults()):
 *   --name TEXT --generations N --depth N --openings-per-pairing N --variety N --max-plies N
 *   --threads N --seed N --yardstick-every N --yardstick-openings N
 *   --yardsticks default,classic,sf:auto   who the champion is measured against (see RunSettings)
 *   --stockfish-from N   Stockfish yardsticks from this generation on
 *   --deep-depth N --deep-share 10-40      percent of games at the deep depth, first-last generation
 * </pre>
 *
 * Ctrl+C stops a run after the generation in progress; {@code resume} continues it.
 */
public final class Cli {

    private Cli() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
        }
        Path file = Path.of(args[1]);
        Map<String, String> options = options(args, switch (args[0]) {
            case "export" -> 3;
            case "champion" -> 4;
            default -> 2;
        });
        switch (args[0]) {
            case "run" -> run(file, options);
            case "resume" -> resume(file, options);
            case "show" -> show(file);
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
                share(o, 0, d.deepShareFirst()), share(o, 1, d.deepShareLast()));
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
                + " | champion FILE GENERATION OUT.json");
        System.exit(2);
    }
}
