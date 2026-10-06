package arena;

import ai.eval.ChessEvaluate;
import ai.eval.Evaluator;
import ai.eval.ParamVector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Command line for the arena:
 *
 * <pre>
 * mvn package -DskipTests
 * java -cp target/izikstar-chess-3.1.0.jar arena.Cli match A B [options]
 *
 *   A, B             "default" (the schema defaults), a preset ("classic": the hand-written weights),
 *                    a parameter file (JSON, see ParamVector), "sf:1500": Stockfish at UCI_Elo
 *                    1500, "fsf" or "fsf:5000": Fairy-Stockfish at full strength, 5000 nodes a move,
 *                    "random": any legal move, or "hof:NAME" (see Players)
 *   --depth N        search depth of both players (default 3)
 *   --openings N     use the first N openings of the suite (default all, about 50); 2 games each
 *   --threads N      games at once (default: cores - 1)
 *   --max-plies N    a game still going after N plies is a draw (default 300)
 *   --pgn FILE       where the games are written as PGN (default runs/arena/A-vs-B-TIME.pgn)
 *   --variety N      how far below the best move (pawn = 100) a move may score (default 20)
 *   --seed N         the same seed repeats the same games (default 1)
 *   --old-search P   P = A or B: that player searches without quiescence, as before Phase 5
 *   --no-speedups P  P = A or B: that player searches without the Phase 5b transposition table
 *                    and move ordering
 *   --move-ms N      give each move N ms, deepening up to --depth (an equal-time match; games then
 *                    depend on the machine and are not repeatable)
 *   --variant ID     play a variant (antichess, king-of-the-hill, three-check) or
 *   --variant-file F a made variant's JSON; weights are then that game's (see Evaluators.schema),
 *                    and the games start from random openings
 *   --opening-plies N  random moves each opening is made of (default 4 in a variant)
 * </pre>
 *
 * It prints each game as it ends, then A's score against B with the Elo difference and its 95%
 * interval.
 */
public final class Cli {

    private Cli() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 3 || !args[0].equals("match")) {
            System.err.println("usage: arena.Cli match A B [--depth N] [--openings N] [--threads N]"
                    + " [--max-plies N] [--variety N] [--seed N] [--old-search A|B] [--no-speedups A|B]"
                    + " [--move-ms N] [--variant ID | --variant-file FILE] [--opening-plies N]");
            System.exit(2);
        }
        Map<String, String> options = new TreeMap<>();
        for (int i = 3; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("expected --option value, got " + args[i]);
            }
            options.put(args[i].substring(2), args[i + 1]);
        }
        int depth = Integer.parseInt(options.getOrDefault("depth", "3"));
        int variety = Integer.parseInt(options.getOrDefault("variety", "20"));
        String oldSearch = options.getOrDefault("old-search", "");
        String noSpeedups = options.getOrDefault("no-speedups", "");
        long moveMillis = Long.parseLong(options.getOrDefault("move-ms", "0"));
        Tournament.Settings defaults = Tournament.Settings.defaults();
        Tournament.Settings settings = new Tournament.Settings(
                Integer.parseInt(options.getOrDefault("max-plies", String.valueOf(defaults.maxPlies()))),
                Integer.parseInt(options.getOrDefault("threads", String.valueOf(defaults.threads()))),
                Long.parseLong(options.getOrDefault("seed", String.valueOf(defaults.seed()))));
        ai.variant.Variant variant = variant(options);
        boolean chess = variant.equals(ai.variant.Variants.CHESS);
        settings = new Tournament.Settings(settings.maxPlies(), settings.threads(), settings.seed(), variant);
        List<Opening> openings = chess && !options.containsKey("opening-plies") ? Opening.suite()
                : Opening.random(variant, Integer.parseInt(options.getOrDefault("openings", "50")),
                Integer.parseInt(options.getOrDefault("opening-plies", "4")), settings.seed());
        if (options.containsKey("openings")) {
            openings = openings.subList(0, Math.min(openings.size(), Integer.parseInt(options.get("openings"))));
        }

        String nameA = name(args[1]);
        String nameB = name(args[2]);
        if (nameA.equals(nameB)) {
            nameA += " (A)";
            nameB += " (B)";
        }
        if (oldSearch.equalsIgnoreCase("A")) {
            nameA += " old search";
        } else if (oldSearch.equalsIgnoreCase("B")) {
            nameB += " old search";
        }
        if (noSpeedups.equalsIgnoreCase("A")) {
            nameA += " no TT";
        } else if (noSpeedups.equalsIgnoreCase("B")) {
            nameB += " no TT";
        }
        Player a = chess ? player(args[1], nameA, depth, variety, !oldSearch.equalsIgnoreCase("A"),
                !noSpeedups.equalsIgnoreCase("A"), moveMillis)
                : Players.parse(args[1], nameA, depth, variety, Players.HALL_OF_FAME, variant);
        Player b = chess ? player(args[2], nameB, depth, variety, !oldSearch.equalsIgnoreCase("B"),
                !noSpeedups.equalsIgnoreCase("B"), moveMillis)
                : Players.parse(args[2], nameB, depth, variety, Players.HALL_OF_FAME, variant);

        int total = 2 * openings.size();
        System.out.printf("%s%s vs %s: %d games at depth %d%s, %d at a time%n", chess ? "" : variant.name() + ": ",
                a.name(), b.name(), total, depth,
                moveMillis > 0 ? " within " + moveMillis + " ms a move" : "", settings.threads());
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        List<GameRecord> games = Tournament.match(a, b, openings, settings, game ->
                System.out.printf("%3d/%d  %-30s %s - %s: %s (%s, %d plies)%n", done.incrementAndGet(), total,
                        game.opening(), game.white(), game.black(), game.result(), game.reason(), game.plies()));
        System.out.printf("%n%s%n%.0f s%n", Score.of(a.name(), games), (System.nanoTime() - start) / 1e9);
        Path pgn = options.containsKey("pgn") ? Path.of(options.get("pgn"))
                : Path.of("runs", "arena", (a.name() + "-vs-" + b.name()).replaceAll("[^A-Za-z0-9._-]+", "_") + "-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pgn");
        Files.createDirectories(pgn.toAbsolutePath().getParent());
        StringBuilder text = new StringBuilder();
        for (GameRecord g : games) {
            text.append(GamePgn.write(variant, g, a.name() + " vs " + b.name(), 1, a.isExternal() ? b.depth() : a.depth())).append('\n');
        }
        Files.writeString(pgn, text);
        System.out.println("games written to " + pgn);
    }

    /** {@code --variant ID} or {@code --variant-file FILE}; chess without either. */
    static ai.variant.Variant variant(Map<String, String> options) throws IOException {
        if (options.containsKey("variant-file")) {
            return ai.variant.VariantJson.read(Files.readString(Path.of(options.get("variant-file"))));
        }
        String id = options.getOrDefault("variant", "chess");
        return ai.variant.Variants.ALL.stream().filter(v -> v.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no built-in variant " + id + "; try --variant-file"));
    }

    private static String name(String spec) {
        return Players.label(spec);
    }

    private static Player player(String spec, String name, int depth, int variety, boolean quiescence,
                                 boolean speedups, long moveMillis) {
        if (!Players.hasWeights(spec)) {
            return Players.parse(spec, name, depth, variety);
        }
        return new Player(name, new ChessEvaluate(Players.params(spec)), depth, variety, quiescence, speedups,
                moveMillis);
    }
}
