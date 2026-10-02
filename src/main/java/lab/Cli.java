package lab;

import arena.GameRecord;
import arena.Score;
import evolution.Evolution;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *
 * run options (defaults in RunSettings.defaults()):
 *   --name TEXT --generations N --depth N --openings-per-pairing N --variety N --max-plies N
 *   --threads N --seed N --yardstick-every N --yardstick-openings N
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
        Map<String, String> options = options(args, args[0].equals("export") ? 3 : 2);
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
            default -> usage();
        }
    }

    private static void run(Path file, Map<String, String> o) {
        RunSettings d = RunSettings.defaults();
        RunSettings settings = new RunSettings(
                integer(o, "generations", d.generations()), integer(o, "depth", d.depth()),
                integer(o, "openings-per-pairing", d.openingsPerPairing()), integer(o, "variety", d.variety()),
                integer(o, "max-plies", d.maxPlies()), integer(o, "threads", d.threads()),
                Long.parseLong(o.getOrDefault("seed", String.valueOf(d.seed()))),
                integer(o, "yardstick-every", d.yardstickEvery()), integer(o, "yardstick-openings", d.yardstickOpenings()));
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
            System.out.printf("generation %3d: champion #%d scored %.0f%% in %d games%s%n", row.number(),
                    row.champion(), 100 * row.championScore(), row.games(),
                    row.yardstick().map(s -> "; vs default weights " + s).orElse(""));
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
                System.out.printf("== generation %d done: champion #%d, %.0f%%%s%n", row.number(), row.champion(),
                        100 * row.championScore(), row.yardstick().map(Score::toString).map(s -> "; " + s).orElse(""));
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

    private static int integer(Map<String, String> options, String name, int fallback) {
        return Integer.parseInt(options.getOrDefault(name, String.valueOf(fallback)));
    }

    private static void usage() {
        System.err.println("usage: lab.Cli run FILE --algorithm CLASS [options] | resume FILE | show FILE | export FILE OUT.csv");
        System.exit(2);
    }
}
