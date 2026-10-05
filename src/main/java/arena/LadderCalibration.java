package arena;

import engine.Cancellation;
import engine.Levels;
import engine.MinimaxEngine;
import engine.SearchRequest;
import engine.StockfishEngine;
import engine.StockfishLocator;
import rules.ChessMove;
import rules.Game;
import rules.MoveResult;
import rules.Position;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Measures the Elo of every level of the difficulty ladder (docs/difficulty-ladder.md). Long:
 * hours for the default 100 games a pairing.
 *
 * <pre>
 * mvn package -DskipTests
 * java -cp target/izikstar-chess-3.1.0.jar arena.LadderCalibration [options]
 *
 *   --games N        games per pairing (default 100; both colours of each opening)
 *   --threads N      games at once (default: cores - 1; each game runs on one core)
 *   --results FILE   where each finished game is appended (default ladder-results.txt). Run the
 *                    same command again to continue an interrupted run: finished games are kept.
 *   --stockfish P    the Stockfish executable (default: the one the game finds)
 *   --report-only    fit and print the table from the results file without playing
 * </pre>
 *
 * <p>Every level plays exactly as in the game: Levels 0-8 are {@link MinimaxEngine} with its 5 s cap,
 * 9-13 are {@link StockfishEngine}. Two kinds of pairing:
 * <ul>
 *   <li>each level against the next one up, which ties the ladder together, down to Level 0;</li>
 *   <li>from Level 3 up, each level against the two anchors around its expected Elo. An anchor is
 *       Stockfish held to a UCI_Elo ({@code UCI_LimitStrength}), half a second a move, the time the
 *       Stockfish levels play at. UCI_Elo is Stockfish's own calibration against the CCRL computer
 *       rating list; it is the outside yardstick, and the only numbers fixed in the fit.</li>
 * </ul>
 *
 * <p>The ratings are the ones that best explain all games at once (Bradley-Terry maximum
 * likelihood), with a 95% interval from 300 parametric bootstrap refits. Levels below Stockfish's
 * lowest UCI_Elo (1320) are rated only through the chain of level-vs-level games.
 */
public final class LadderCalibration {

    /** Stockfish's UCI_Elo anchors. 3190 is the highest UCI_Elo Stockfish accepts. */
    static final int[] ANCHORS = {1320, 1500, 1700, 1900, 2100, 2300, 2500, 2700, 2900, 3190};
    /** The expected Elo of each level (the research measurement), to pick its anchors. */
    static final int[] EXPECTED = {-1150, 100, 460, 750, 1070, 1260, 1530, 1690, 1910, 2150, 2400, 2650, 2900, 3190};
    static final long ANCHOR_MOVE_MS = 500;
    static final int MAX_PLIES = 300;
    static final int BOOTSTRAP = 300;

    private LadderCalibration() {}

    /** One side of a game: a level ("L5") or an anchor ("SF1900"). */
    record Pairing(String a, String b) {
        String key() {
            return a + " " + b;
        }
    }

    /** Wins, draws and losses of {@code a} against {@code b}. */
    static final class Tally {
        int wins, draws, losses;

        int games() {
            return wins + draws + losses;
        }

        double score() {
            return wins + 0.5 * draws;
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = new TreeMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("expected --option, got " + args[i]);
            }
            boolean flag = i + 1 >= args.length || args[i + 1].startsWith("--");
            options.put(args[i].substring(2), flag ? "true" : args[++i]);
        }
        int games = Integer.parseInt(options.getOrDefault("games", "100"));
        int threads = Integer.parseInt(options.getOrDefault("threads",
                String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() - 1))));
        Path results = Path.of(options.getOrDefault("results", "ladder-results.txt"));
        String stockfish = options.containsKey("stockfish") ? options.get("stockfish")
                : StockfishLocator.find().map(Path::toString).orElse(null);

        List<Pairing> pairings = pairings();
        Map<String, Tally> tallies = load(results);
        if (!options.containsKey("report-only")) {
            if (stockfish == null) {
                System.err.println("Stockfish not found: install it or pass --stockfish PATH");
                System.exit(2);
            }
            play(pairings, tallies, games, threads, results, stockfish);
        }
        System.out.println();
        System.out.print(report(pairings, tallies));
    }

    /** Neighbouring levels, then each level from 3 up against the anchors around it. */
    static List<Pairing> pairings() {
        List<Pairing> list = new ArrayList<>();
        for (int level = Levels.RANDOM; level < Levels.MAX; level++) {
            list.add(new Pairing("L" + level, "L" + (level + 1)));
        }
        for (int level = 3; level <= Levels.MAX; level++) {
            for (int anchor : anchorsAround(EXPECTED[level])) {
                list.add(new Pairing("L" + level, "SF" + anchor));
            }
        }
        return list;
    }

    /** The anchor at or just below {@code elo} and the one above (one anchor at the ends). */
    static List<Integer> anchorsAround(int elo) {
        List<Integer> around = new ArrayList<>();
        int below = -1;
        for (int i = 0; i < ANCHORS.length; i++) {
            if (ANCHORS[i] <= elo) {
                below = i;
            }
        }
        if (below >= 0) {
            around.add(ANCHORS[below]);
        }
        if (below + 1 < ANCHORS.length && ANCHORS[Math.max(0, below)] != elo) {
            around.add(ANCHORS[below + 1]);
        }
        return around;
    }

    // ---- playing --------------------------------------------------------------------------

    private static void play(List<Pairing> pairings, Map<String, Tally> tallies, int games, int threads,
                             Path results, String stockfish) throws Exception {
        List<Opening> openings = Opening.suite();
        record Job(Pairing pairing, int index) {}
        List<Job> jobs = new ArrayList<>();
        for (Pairing p : pairings) {
            int done = tallies.containsKey(p.key()) ? tallies.get(p.key()).games() : 0;
            for (int i = done; i < games; i++) {
                jobs.add(new Job(p, i));
            }
        }
        System.out.printf(Locale.ROOT, "%d pairings, %d games to play on %d threads (results in %s)%n",
                pairings.size(), jobs.size(), threads, results.toAbsolutePath());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        long start = System.nanoTime();
        int[] finished = {0};
        for (Job job : jobs) {
            futures.add(pool.submit(() -> {
                Opening opening = openings.get((job.index() / 2) % openings.size());
                boolean aWhite = job.index() % 2 == 0;
                double score = playGame(job.pairing(), opening, aWhite, job.index(), stockfish);
                synchronized (tallies) {
                    Tally t = tallies.computeIfAbsent(job.pairing().key(), k -> new Tally());
                    if (score == 1) t.wins++;
                    else if (score == 0) t.losses++;
                    else t.draws++;
                    append(results, job.pairing().key() + " " + score);
                    finished[0]++;
                    double minutes = (System.nanoTime() - start) / 6e10;
                    System.out.printf(Locale.ROOT, "%d/%d  %s %s %s  %.1f  (%.0f min, about %.0f min left)%n",
                            finished[0], jobs.size(), job.pairing().a(), aWhite ? "(white)" : "(black)",
                            job.pairing().b(), score, minutes, minutes / finished[0] * (jobs.size() - finished[0]));
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
    }

    /** {@code a}'s score: 1, 0.5 or 0. */
    static double playGame(Pairing pairing, Opening opening, boolean aWhite, long seed, String stockfish) {
        try (Side a = side(pairing.a(), seed, stockfish); Side b = side(pairing.b(), ~seed, stockfish)) {
            Game game = new Game();
            for (String uci : opening.moves()) {
                game.play(uci);
            }
            while (!game.status().isGameOver() && game.plyCount() < MAX_PLIES) {
                boolean whiteToMove = game.fen().split(" ")[1].equals("w");
                List<ChessMove> moves = game.moves().stream().map(MoveResult::move).toList();
                ChessMove move = (whiteToMove == aWhite ? a : b).move(game.fen(), moves);
                if (move == null) {
                    throw new IllegalStateException((whiteToMove == aWhite ? pairing.a() : pairing.b())
                            + " gave no move in " + game.fen());
                }
                game.play(move);
            }
            String score = game.status().result(game.fen().split(" ")[1].equals("w"));
            if (score != null && !score.equals("1/2-1/2")) {
                return score.equals("1-0") == aWhite ? 1 : 0;
            }
            return 0.5;
        }
    }

    interface Side extends AutoCloseable {
        ChessMove move(String fen, List<ChessMove> moves);

        @Override
        void close();
    }

    private static Side side(String name, long seed, String stockfish) {
        if (name.startsWith("SF")) {
            return new Anchor(stockfish, Integer.parseInt(name.substring(2)));
        }
        int level = Integer.parseInt(name.substring(1));
        if (Levels.isStockfish(level)) {
            StockfishEngine engine = new StockfishEngine(List.of(stockfish));
            return new Side() {
                public ChessMove move(String fen, List<ChessMove> moves) {
                    return engine.bestMove(new SearchRequest(fen, Position.START_FEN, moves, level, Cancellation.NONE));
                }

                public void close() {
                    engine.close();
                }
            };
        }
        MinimaxEngine engine = new MinimaxEngine(new Random(seed));
        return new Side() {
            public ChessMove move(String fen, List<ChessMove> moves) {
                return engine.bestMove(new SearchRequest(fen, Position.START_FEN, moves, level, Cancellation.NONE));
            }

            public void close() {}
        };
    }

    /** Stockfish held to a UCI_Elo, {@link #ANCHOR_MOVE_MS} a move. */
    private static final class Anchor implements Side {
        private final Process process;
        private final BufferedReader in;
        private final PrintWriter out;

        Anchor(String stockfish, int elo) {
            try {
                process = new ProcessBuilder(stockfish).redirectErrorStream(true).start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println("uci");
            out.println("setoption name Threads value 1");
            out.println("setoption name UCI_LimitStrength value true");
            out.println("setoption name UCI_Elo value " + elo);
            out.println("ucinewgame");
            out.println("isready");
            await("readyok");
        }

        @Override
        public ChessMove move(String fen, List<ChessMove> moves) {
            StringBuilder position = new StringBuilder("position startpos");
            if (!moves.isEmpty()) {
                position.append(" moves");
                moves.forEach(m -> position.append(' ').append(m.toUci()));
            }
            out.println(position);
            out.println("go movetime " + ANCHOR_MOVE_MS);
            String line = await("bestmove");
            return ChessMove.fromUci(line.split("\\s+")[1]);
        }

        private String await(String prefix) {
            try {
                for (String line; (line = in.readLine()) != null; ) {
                    if (line.startsWith(prefix)) {
                        return line;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            throw new IllegalStateException("Stockfish stopped before '" + prefix + "'");
        }

        @Override
        public void close() {
            out.println("quit");
            process.destroy();
        }
    }

    // ---- results file ---------------------------------------------------------------------

    private static Map<String, Tally> load(Path results) throws IOException {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        if (!Files.exists(results)) {
            return tallies;
        }
        for (String line : Files.readAllLines(results)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3) {
                continue;
            }
            Tally t = tallies.computeIfAbsent(parts[0] + " " + parts[1], k -> new Tally());
            double score = Double.parseDouble(parts[2]);
            if (score == 1) t.wins++;
            else if (score == 0) t.losses++;
            else t.draws++;
        }
        return tallies;
    }

    private static void append(Path results, String line) {
        try {
            Files.writeString(results, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- fitting --------------------------------------------------------------------------

    /** The fitted Elo of each player; anchors stay at their UCI_Elo. */
    static Map<String, Double> fit(Map<String, Tally> tallies) {
        Map<String, Double> elo = new TreeMap<>();
        for (String key : tallies.keySet()) {
            for (String player : key.split(" ")) {
                elo.putIfAbsent(player, player.startsWith("SF") ? Double.parseDouble(player.substring(2)) : 1500.0);
            }
        }
        // maximum likelihood by Newton steps, one player at a time; half a draw is added to each
        // pairing so that a 0% or 100% score still gives a finite rating
        double k = Math.log(10) / 400;
        for (int iteration = 0; iteration < 1000; iteration++) {
            double largest = 0;
            for (String player : elo.keySet()) {
                if (player.startsWith("SF")) {
                    continue;
                }
                double first = 0;
                double second = 0;
                for (Map.Entry<String, Tally> e : tallies.entrySet()) {
                    String[] ab = e.getKey().split(" ");
                    boolean isA = ab[0].equals(player);
                    if (!isA && !ab[1].equals(player)) {
                        continue;
                    }
                    Tally t = e.getValue();
                    double n = t.games() + 1;
                    double score = isA ? t.score() + 0.5 : n - t.score() - 0.5;
                    double other = elo.get(isA ? ab[1] : ab[0]);
                    double p = 1 / (1 + Math.pow(10, (other - elo.get(player)) / 400));
                    first += score - n * p;
                    second += n * p * (1 - p);
                }
                double step = Math.max(-200, Math.min(200, first / (k * second)));
                elo.merge(player, step, Double::sum);
                largest = Math.max(largest, Math.abs(step));
            }
            if (largest < 0.01) {
                break;
            }
        }
        return elo;
    }

    /** The table: each level's Elo with its 95% interval, the step from the level below, and every pairing. */
    static String report(List<Pairing> pairings, Map<String, Tally> tallies) {
        Map<String, Tally> played = new LinkedHashMap<>();
        for (Pairing p : pairings) {
            Tally t = tallies.get(p.key());
            if (t != null && t.games() > 0) {
                played.put(p.key(), t);
            }
        }
        StringBuilder out = new StringBuilder();
        if (played.isEmpty()) {
            return "no games yet\n";
        }
        Map<String, Double> elo = fit(played);
        Map<String, List<Double>> samples = new TreeMap<>();
        Random random = new Random(1);
        for (int b = 0; b < BOOTSTRAP; b++) {
            Map<String, Tally> resampled = new LinkedHashMap<>();
            for (Map.Entry<String, Tally> e : played.entrySet()) {
                Tally t = e.getValue();
                Tally r = new Tally();
                for (int g = 0; g < t.games(); g++) {
                    int x = random.nextInt(t.games());
                    if (x < t.wins) r.wins++;
                    else if (x < t.wins + t.draws) r.draws++;
                    else r.losses++;
                }
                resampled.put(e.getKey(), r);
            }
            fit(resampled).forEach((player, value) -> samples.computeIfAbsent(player, k -> new ArrayList<>()).add(value));
        }

        out.append("| Level | Elo | 95% interval | Step |\n|---|---|---|---|\n");
        Double previous = null;
        for (int level = Levels.RANDOM; level <= Levels.MAX; level++) {
            String player = "L" + level;
            if (!elo.containsKey(player)) {
                continue;
            }
            List<Double> s = new ArrayList<>(samples.get(player));
            s.sort(Double::compare);
            double value = elo.get(player);
            out.append(String.format(Locale.ROOT, "| %d | %.0f | %.0f to %.0f | %s |%n", level, value,
                    s.get((int) (s.size() * 0.025)), s.get((int) (s.size() * 0.975) - 1),
                    previous == null ? "" : String.format(Locale.ROOT, "%+.0f", value - previous)));
            previous = value;
        }
        out.append("\n| Pairing | Games | Score of the first | Wins / draws / losses |\n|---|---|---|---|\n");
        for (Map.Entry<String, Tally> e : played.entrySet()) {
            Tally t = e.getValue();
            out.append(String.format(Locale.ROOT, "| %s vs %s | %d | %.1f%% | %d / %d / %d |%n",
                    e.getKey().split(" ")[0], e.getKey().split(" ")[1], t.games(), 100 * t.score() / t.games(),
                    t.wins, t.draws, t.losses));
        }
        return out.toString();
    }
}
