package lab;

import ai.Minimax;
import ai.piece.PieceType;
import ai.variant.Variant;
import arena.ExternalEngine;
import arena.FairyStockfish;
import arena.UciSession;
import engine.MinimaxEngine;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * The variant health check (Phase 6 R5d): the built-in engine plays a variant against itself and
 * the games say whether the variant is any good to play. Does one side win too often? Is it a
 * draw every time, over too soon, or never over? How many moves does a player have to choose from?
 *
 * <p>Every game starts with a few random moves, so the games differ, then each side searches to its
 * depth with a little variety. A game still going after {@link Settings#maxPlies} is stopped and
 * counted as a draw ({@code PLY_CAP}). The same settings always give the same games (Fairy-Stockfish
 * games aside: it is not ours to make repeatable).
 *
 * <p>Besides the old summary the report has the statistics behind it ({@link Details}): the results
 * with 95% intervals, every ending, the length distribution, the choices and material ply by ply,
 * captures, checks, what each piece type did, and a few of the games' moves.
 */
public final class VariantHealth {

    private VariantHealth() {}

    /** Who plays the games. */
    public enum Engine {
        /** The built-in engine at each side's depth. */
        BUILT_IN,
        /** Fairy-Stockfish at {@link Settings#fairyNodes} a move, when installed and it knows the variant. */
        FAIRY_STOCKFISH
    }

    /**
     * @param games       how many games to play
     * @param whiteDepth  the search depth of White (the built-in engine)
     * @param blackDepth  the search depth of Black
     * @param randomPlies random moves at the start of each game
     * @param maxPlies    a game this long is stopped and scored as a draw
     * @param seed        the games' randomness
     * @param threads     games played at once; 0 = every core but one
     * @param variety     the built-in engine picks at random among moves this close to the best (pawn = 100)
     * @param engine      who plays
     * @param fairyNodes  Fairy-Stockfish's nodes a move
     */
    public record Settings(int games, int whiteDepth, int blackDepth, int randomPlies, int maxPlies, long seed,
                           int threads, int variety, Engine engine, long fairyNodes) {

        public static final int MAX_GAMES = 1000;
        public static final int MAX_DEPTH = 5;
        public static final int MAX_RANDOM_PLIES = 40;
        public static final int MAX_PLIES = 2000;
        public static final int MAX_VARIETY = 500;
        public static final int MAX_THREADS = 64;
        public static final long MIN_FAIRY_NODES = 100, MAX_FAIRY_NODES = 5_000_000;

        public Settings {
            if (games < 1 || games > MAX_GAMES) {
                throw new IllegalArgumentException("games is 1 to " + MAX_GAMES + ": " + games);
            }
            for (int depth : new int[]{whiteDepth, blackDepth}) {
                if (depth < 1 || depth > MAX_DEPTH) {
                    throw new IllegalArgumentException("depth is 1 to " + MAX_DEPTH + ": " + depth);
                }
            }
            if (randomPlies < 0 || randomPlies > MAX_RANDOM_PLIES || maxPlies < 10 || maxPlies > MAX_PLIES) {
                throw new IllegalArgumentException("randomPlies is 0 to " + MAX_RANDOM_PLIES + " and maxPlies 10 to " + MAX_PLIES);
            }
            if (threads < 0 || threads > MAX_THREADS) {
                throw new IllegalArgumentException("threads is 0 (automatic) to " + MAX_THREADS + ": " + threads);
            }
            if (variety < 0 || variety > MAX_VARIETY) {
                throw new IllegalArgumentException("variety is 0 to " + MAX_VARIETY + " centipawns: " + variety);
            }
            if (engine == null) {
                engine = Engine.BUILT_IN;
            }
            if (fairyNodes < MIN_FAIRY_NODES || fairyNodes > MAX_FAIRY_NODES) {
                throw new IllegalArgumentException("Fairy-Stockfish nodes are " + MIN_FAIRY_NODES + " to " + MAX_FAIRY_NODES);
            }
        }

        /** Both sides at {@code depth}, every core but one, the built-in engine's usual variety. */
        public Settings(int games, int depth, int randomPlies, int maxPlies, long seed) {
            this(games, depth, depth, randomPlies, maxPlies, seed, 0, MinimaxEngine.DEFAULT_VARIETY, Engine.BUILT_IN,
                    FairyStockfish.DEFAULT_NODES);
        }

        public static Settings of(int games, int depth) {
            return new Settings(games, depth, 4, 300, 2026);
        }

        /** The old single depth: White's. */
        public int depth() {
            return whiteDepth;
        }

        /** The threads the games run on. */
        public int threadCount() {
            int auto = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            return Math.max(1, Math.min(games, threads == 0 ? auto : threads));
        }
    }

    /**
     * One game, with what happened in it.
     *
     * @param legal        legal moves in each position before a move, one per ply
     * @param whiteMaterial White's material (royal pieces left out) in each position, the start included
     * @param pieces       pieces on the board in each position
     * @param counts       piece letter (upper case) -> its count on the board, summed over every position
     * @param start        piece letter -> both sides' count at the start
     * @param end          piece letter -> both sides' count at the end
     * @param moved        piece letter -> its moves
     * @param took         piece letter -> its captures
     * @param lost         piece letter -> times one was captured
     * @param promoted     piece letter -> promotions to it
     * @param whiteChecked times White was left in check
     * @param firstCapture the ply of the first capture (1 = White's first move), or 0
     */
    public record GameResult(int index, String result, String ending, int plies, int[] legal, int[] whiteMaterial,
                             int[] blackMaterial, int[] pieces, Map<Character, Long> counts, Map<Character, Integer> start,
                             Map<Character, Integer> end, Map<Character, Integer> moved, Map<Character, Integer> took,
                             Map<Character, Integer> lost, Map<Character, Integer> promoted, int captures,
                             int firstCapture, int whiteChecked, int blackChecked, boolean whiteFirst,
                             List<String> uci, List<String> san) {

        public long legalMoves() {
            return Arrays.stream(legal).asLongStream().sum();
        }

        public boolean decisive() {
            return !result.equals("1/2-1/2");
        }
    }

    /** A share of the games with its 95% (Wilson) interval. */
    public record Interval(double value, double low, double high) {}

    /** How a set of numbers spreads. */
    public record Spread(int count, double mean, int min, int p10, int median, int p90, int max) {}

    /**
     * The results by colour. {@code firstMover} is "white" or "black" (the start position says);
     * {@code firstMoverShare} is the share of the decisive games the side moving first won.
     */
    public record Results(Interval whiteWins, Interval blackWins, Interval draws, Interval whiteScore,
                          String firstMover, int firstMoverWins, double firstMoverShare) {}

    /** One way a game ended: how often, and who won that way. */
    public record Ending(String reason, int count, double share, int whiteWins, int blackWins, int draws) {}

    /** Games whose length is {@code from} to {@code to - 1} plies. */
    public record LengthBucket(int from, int to, int all, int decisive, int drawn) {}

    public record Lengths(Spread all, Spread decisive, Spread drawn, List<LengthBucket> histogram) {}

    /** Positions at plies {@code from} to {@code to - 1}: the average choices, material and pieces. */
    public record PlyBucket(int from, int to, int games, double legalMoves, double whiteMaterial, double blackMaterial,
                            double pieces) {}

    /** The choices a player had: overall, by side, and by ply. */
    public record Branching(double mean, double white, double black, Spread positions, List<PlyBucket> byPly) {}

    public record Captures(Spread perGame, Spread firstCapturePly, int gamesWithoutCapture) {}

    /** Average material (pawn = 100, royal pieces left out) and pieces on the board over the games. */
    public record Material(double whiteStart, double blackStart, double white, double black, double whiteEnd,
                           double blackEnd, double piecesStart, double pieces, double piecesEnd) {}

    /**
     * What one piece type did, both sides together, per game: moves, captures made, times captured,
     * how many stood at the start and at the end, and the share of the moves it made.
     */
    public record PieceStats(String letter, String name, int value, boolean royal, double start, double end,
                             double onBoard, double moves, double moveShare, double captures, double captured,
                             double promotions, double survival) {}

    /** Times each side was left in check, and in how many games. */
    public record Checks(int white, int black, double whitePerGame, double blackPerGame, int whiteGames, int blackGames) {}

    /** A game to replay: its moves in UCI and SAN. */
    public record SampleGame(String label, int index, String result, String ending, int plies, List<String> uci,
                             List<String> san) {}

    public record Details(Results results, List<Ending> endings, Lengths lengths, Branching branching, Captures captures,
                          Material material, List<PieceStats> pieces, Checks checks, List<SampleGame> samples) {}

    /**
     * What the games say.
     *
     * @param endings      how the games ended, by {@link GameStatus} name or {@code PLY_CAP}
     * @param movesPerTurn the average number of legal moves a player had to choose from
     * @param notes        plain words on what looks wrong (one side wins too often, all draws...)
     * @param details      the statistics behind it
     */
    public record Report(int games, int whiteWins, int blackWins, int draws, double averagePlies, int shortest,
                         int longest, double movesPerTurn, Map<String, Integer> endings, List<String> notes,
                         Details details) {

        public double whiteScore() {
            return games == 0 ? 0 : (whiteWins + draws / 2.0) / games;
        }

        public double decisiveShare() {
            return games == 0 ? 0 : (whiteWins + blackWins) / (double) games;
        }
    }

    /** Refuses settings this variant or computer cannot play: Fairy-Stockfish missing or not knowing it. */
    public static void check(Variant variant, Settings settings) {
        if (settings.engine() == Engine.FAIRY_STOCKFISH) {
            if (!FairyStockfish.plays(variant)) {
                throw new IllegalArgumentException("Fairy-Stockfish cannot play " + variant.name());
            }
            if (!FairyStockfish.installed()) {
                throw new IllegalArgumentException("Fairy-Stockfish is not installed");
            }
        }
    }

    /** Plays the games on {@link Settings#threadCount()} threads; {@code progress} hears each finished game's count. */
    public static Report run(Variant variant, Settings settings, IntConsumer progress, BooleanSupplier cancel) {
        check(variant, settings);
        ExecutorService pool = Executors.newFixedThreadPool(settings.threadCount(), r -> {
            Thread t = new Thread(r, "variant-health");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<GameResult>> futures = new ArrayList<>();
            for (int i = 0; i < settings.games(); i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    GameResult r = play(variant, settings, index, cancel);
                    progress.accept(done.incrementAndGet());
                    return r;
                }));
            }
            List<GameResult> results = new ArrayList<>();
            for (Future<GameResult> f : futures) {
                GameResult r = f.get();
                if (r != null) {
                    results.add(r);
                }
            }
            return report(variant, results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /** Game {@code index} of the engine against itself; {@code null} if cancelled. */
    static GameResult play(Variant variant, Settings settings, int index, BooleanSupplier cancel) {
        long seed = settings.seed() * 1_000_003L + index;
        Random random = new Random(seed);
        Minimax.Options options = new Minimax.Options(settings.variety(), random, true);
        boolean fairy = settings.engine() == Engine.FAIRY_STOCKFISH;
        try (UciSession uci = fairy ? new UciSession(engine(variant, settings)) : null) {
            return play(variant, settings, index, random, options, uci, cancel);
        }
    }

    private static ExternalEngine engine(Variant variant, Settings settings) {
        return FairyStockfish.engine(variant, settings.fairyNodes());
    }

    private static GameResult play(Variant variant, Settings settings, int index, Random random,
                                   Minimax.Options options, UciSession uci, BooleanSupplier cancel) {
        Game game = new Game(variant);
        Map<Character, PieceType> types = new LinkedHashMap<>();
        variant.pieces().forEach(p -> types.put(p.letter(), p));
        boolean whiteFirst = game.fen().split(" ")[1].equals("w");
        List<Integer> legal = new ArrayList<>();
        List<int[]> material = new ArrayList<>(); // white, black, pieces
        Map<Character, Long> counts = new TreeMap<>();
        Map<Character, Integer> moved = new TreeMap<>();
        Map<Character, Integer> took = new TreeMap<>();
        Map<Character, Integer> lost = new TreeMap<>();
        Map<Character, Integer> promoted = new TreeMap<>();
        Map<Character, Integer> start = census(game.fen());
        Map<Character, Integer> now = start;
        material.add(material(game.fen(), types));
        now.forEach((k, v) -> counts.merge(k, (long) v, Long::sum));
        List<String> ucis = new ArrayList<>();
        List<String> sans = new ArrayList<>();
        int captures = 0;
        int firstCapture = 0;
        int whiteChecked = 0;
        int blackChecked = 0;
        while (!game.status().isGameOver() && game.plyCount() < settings.maxPlies()) {
            if (cancel.getAsBoolean()) {
                return null;
            }
            List<ChessMove> moves = game.legalMoves();
            legal.add(moves.size());
            boolean whiteToMove = game.fen().split(" ")[1].equals("w");
            ChessMove move;
            if (game.plyCount() < settings.randomPlies()) {
                move = moves.get(random.nextInt(moves.size()));
            } else if (uci != null) {
                move = uci.move(ucis);
            } else {
                int depth = whiteToMove ? settings.whiteDepth() : settings.blackDepth();
                move = MinimaxEngine.searchAtDepth(variant, game.history(), depth, options, cancel);
            }
            if (move == null) {
                return null; // cancelled; a position with no legal move is game over above
            }
            MoveResult played = game.play(move);
            ucis.add(played.move().toUci());
            sans.add(played.san());
            char piece = Character.toUpperCase(played.piece());
            moved.merge(piece, 1, Integer::sum);
            if (played.isCapture() && Character.isUpperCase(played.captured()) != played.whiteMoved()) {
                captures++;
                if (firstCapture == 0) {
                    firstCapture = game.plyCount();
                }
                took.merge(piece, 1, Integer::sum);
                lost.merge(Character.toUpperCase(played.captured()), 1, Integer::sum);
            }
            if (played.isPromotion()) {
                promoted.merge(Character.toUpperCase(played.move().promotion()), 1, Integer::sum);
            }
            if (!played.checked().isEmpty()) {
                if (played.whiteMoved()) {
                    blackChecked++;
                } else {
                    whiteChecked++;
                }
            }
            now = census(game.fen());
            material.add(material(game.fen(), types));
            now.forEach((k, v) -> counts.merge(k, (long) v, Long::sum));
        }
        GameStatus status = game.status();
        boolean whiteToMove = game.fen().split(" ")[1].equals("w");
        String result = status.isGameOver() ? status.result(whiteToMove) : "1/2-1/2";
        return new GameResult(index, result, status.isGameOver() ? status.name() : "PLY_CAP", game.plyCount(),
                legal.stream().mapToInt(Integer::intValue).toArray(),
                material.stream().mapToInt(m -> m[0]).toArray(), material.stream().mapToInt(m -> m[1]).toArray(),
                material.stream().mapToInt(m -> m[2]).toArray(), counts, start, now, moved, took, lost, promoted,
                captures, firstCapture, whiteChecked, blackChecked, whiteFirst, List.copyOf(ucis), List.copyOf(sans));
    }

    /** Piece letter (upper case) -> both sides' count, from the FEN's placement. */
    static Map<Character, Integer> census(String fen) {
        Map<Character, Integer> out = new TreeMap<>();
        String placement = fen.trim().split("\\s+")[0];
        for (int i = 0; i < placement.length(); i++) {
            char c = placement.charAt(i);
            if (Character.isLetter(c)) {
                out.merge(Character.toUpperCase(c), 1, Integer::sum);
            }
        }
        return out;
    }

    /** {White's material, Black's material, pieces}: royal pieces count as pieces but not material. */
    private static int[] material(String fen, Map<Character, PieceType> types) {
        int[] out = new int[3];
        String placement = fen.trim().split("\\s+")[0];
        for (int i = 0; i < placement.length(); i++) {
            char c = placement.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            out[2]++;
            PieceType type = types.get(Character.toUpperCase(c));
            if (type != null && !type.royal()) {
                out[Character.isUpperCase(c) ? 0 : 1] += type.value();
            }
        }
        return out;
    }

    static Report report(Variant variant, List<GameResult> results) {
        int white = 0;
        int black = 0;
        int draws = 0;
        long plies = 0;
        long moves = 0;
        int shortest = Integer.MAX_VALUE;
        int longest = 0;
        Map<String, Integer> endings = new TreeMap<>();
        for (GameResult r : results) {
            switch (r.result()) {
                case "1-0" -> white++;
                case "0-1" -> black++;
                default -> draws++;
            }
            plies += r.plies();
            moves += r.legalMoves();
            shortest = Math.min(shortest, r.plies());
            longest = Math.max(longest, r.plies());
            endings.merge(r.ending(), 1, Integer::sum);
        }
        int n = results.size();
        double averagePlies = n == 0 ? 0 : plies / (double) n;
        double movesPerTurn = plies == 0 ? 0 : moves / (double) plies;
        List<String> notes = notes(n, white, black, draws, averagePlies, movesPerTurn, endings.getOrDefault("PLY_CAP", 0));
        return new Report(n, white, black, draws, averagePlies, n == 0 ? 0 : shortest, longest, movesPerTurn, endings,
                notes, details(variant, results));
    }

    static Details details(Variant variant, List<GameResult> games) {
        return new Details(results(variant, games), endings(games), lengths(games), branching(games), captures(games),
                material(games), pieces(variant, games), checks(games), samples(games));
    }

    static Results results(Variant variant, List<GameResult> games) {
        int n = games.size();
        int white = count(games, g -> g.result().equals("1-0"));
        int black = count(games, g -> g.result().equals("0-1"));
        int draws = n - white - black;
        boolean whiteFirst = games.isEmpty() ? variant.startFen().trim().split("\\s+")[1].equals("w")
                : games.get(0).whiteFirst();
        int firstWins = whiteFirst ? white : black;
        double score = n == 0 ? 0 : (white + draws / 2.0) / n;
        double variance = 0;
        for (GameResult g : games) {
            double s = switch (g.result()) {
                case "1-0" -> 1;
                case "0-1" -> 0;
                default -> 0.5;
            };
            variance += (s - score) * (s - score);
        }
        double half = n < 2 ? 0.5 : 1.96 * Math.sqrt(variance / (n - 1) / n);
        Interval whiteScore = new Interval(score, Math.max(0, score - half), Math.min(1, score + half));
        return new Results(wilson(white, n), wilson(black, n), wilson(draws, n), whiteScore,
                whiteFirst ? "white" : "black", firstWins, white + black == 0 ? 0 : firstWins / (double) (white + black));
    }

    /** The share {@code k / n} with its 95% Wilson score interval. */
    static Interval wilson(int k, int n) {
        if (n == 0) {
            return new Interval(0, 0, 1);
        }
        double z = 1.96;
        double p = k / (double) n;
        double denominator = 1 + z * z / n;
        double centre = (p + z * z / (2.0 * n)) / denominator;
        double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / denominator;
        return new Interval(p, Math.max(0, centre - half), Math.min(1, centre + half));
    }

    static List<Ending> endings(List<GameResult> games) {
        Map<String, int[]> by = new TreeMap<>();
        for (GameResult g : games) {
            int[] row = by.computeIfAbsent(g.ending(), k -> new int[4]);
            row[0]++;
            row[switch (g.result()) {
                case "1-0" -> 1;
                case "0-1" -> 2;
                default -> 3;
            }]++;
        }
        List<Ending> out = new ArrayList<>();
        by.forEach((reason, row) -> out.add(new Ending(reason, row[0], row[0] / (double) games.size(), row[1], row[2], row[3])));
        out.sort((a, b) -> b.count() - a.count());
        return out;
    }

    /** The spread of {@code values}: nearest-rank percentiles. */
    static Spread spread(int[] values) {
        if (values.length == 0) {
            return new Spread(0, 0, 0, 0, 0, 0, 0);
        }
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return new Spread(sorted.length, Arrays.stream(sorted).average().orElse(0), sorted[0], rank(sorted, 0.1),
                rank(sorted, 0.5), rank(sorted, 0.9), sorted[sorted.length - 1]);
    }

    private static int rank(int[] sorted, double q) {
        return sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(q * sorted.length) - 1))];
    }

    /** A round bucket width that cuts {@code range} into at most about {@code buckets} pieces. */
    static int width(int range, int buckets) {
        for (int w : new int[]{1, 2, 4, 5, 10, 20, 25, 50, 100, 200, 250, 500}) {
            if (range / w < buckets) {
                return w;
            }
        }
        return 1000;
    }

    static Lengths lengths(List<GameResult> games) {
        int[] all = games.stream().mapToInt(GameResult::plies).toArray();
        int[] decisive = games.stream().filter(GameResult::decisive).mapToInt(GameResult::plies).toArray();
        int[] drawn = games.stream().filter(g -> !g.decisive()).mapToInt(GameResult::plies).toArray();
        List<LengthBucket> histogram = new ArrayList<>();
        if (all.length > 0) {
            int min = Arrays.stream(all).min().orElse(0);
            int max = Arrays.stream(all).max().orElse(0);
            int w = width(max - min, 16);
            for (int from = min / w * w; from <= max; from += w) {
                int lo = from;
                int hi = from + w;
                histogram.add(new LengthBucket(lo, hi, (int) Arrays.stream(all).filter(p -> p >= lo && p < hi).count(),
                        (int) Arrays.stream(decisive).filter(p -> p >= lo && p < hi).count(),
                        (int) Arrays.stream(drawn).filter(p -> p >= lo && p < hi).count()));
            }
        }
        return new Lengths(spread(all), spread(decisive), spread(drawn), histogram);
    }

    static Branching branching(List<GameResult> games) {
        long white = 0;
        long whiteTurns = 0;
        long black = 0;
        long blackTurns = 0;
        int longest = 0;
        List<Integer> every = new ArrayList<>();
        for (GameResult g : games) {
            for (int ply = 0; ply < g.legal().length; ply++) {
                boolean whiteToMove = (ply % 2 == 0) == g.whiteFirst();
                if (whiteToMove) {
                    white += g.legal()[ply];
                    whiteTurns++;
                } else {
                    black += g.legal()[ply];
                    blackTurns++;
                }
                every.add(g.legal()[ply]);
            }
            longest = Math.max(longest, g.plies());
        }
        int w = Math.max(2, width(longest, 25));
        List<PlyBucket> byPly = new ArrayList<>();
        for (int from = 0; from <= longest; from += w) {
            int lo = from;
            int hi = from + w;
            int reached = 0;
            long legal = 0;
            long legalCount = 0;
            long whiteMaterial = 0;
            long blackMaterial = 0;
            long pieces = 0;
            long positions = 0;
            for (GameResult g : games) {
                if (g.plies() >= lo) {
                    reached++;
                }
                for (int ply = lo; ply < hi && ply < g.whiteMaterial().length; ply++) {
                    whiteMaterial += g.whiteMaterial()[ply];
                    blackMaterial += g.blackMaterial()[ply];
                    pieces += g.pieces()[ply];
                    positions++;
                    if (ply < g.legal().length) {
                        legal += g.legal()[ply];
                        legalCount++;
                    }
                }
            }
            byPly.add(new PlyBucket(lo, hi, reached, legalCount == 0 ? 0 : legal / (double) legalCount,
                    positions == 0 ? 0 : whiteMaterial / (double) positions,
                    positions == 0 ? 0 : blackMaterial / (double) positions, positions == 0 ? 0 : pieces / (double) positions));
        }
        long total = white + black;
        long turns = whiteTurns + blackTurns;
        return new Branching(turns == 0 ? 0 : total / (double) turns, whiteTurns == 0 ? 0 : white / (double) whiteTurns,
                blackTurns == 0 ? 0 : black / (double) blackTurns,
                spread(every.stream().mapToInt(Integer::intValue).toArray()), byPly);
    }

    static Captures captures(List<GameResult> games) {
        return new Captures(spread(games.stream().mapToInt(GameResult::captures).toArray()),
                spread(games.stream().filter(g -> g.firstCapture() > 0).mapToInt(GameResult::firstCapture).toArray()),
                count(games, g -> g.firstCapture() == 0));
    }

    static Material material(List<GameResult> games) {
        double[] sums = new double[9];
        for (GameResult g : games) {
            int last = g.whiteMaterial().length - 1;
            sums[0] += g.whiteMaterial()[0];
            sums[1] += g.blackMaterial()[0];
            sums[2] += Arrays.stream(g.whiteMaterial()).average().orElse(0);
            sums[3] += Arrays.stream(g.blackMaterial()).average().orElse(0);
            sums[4] += g.whiteMaterial()[last];
            sums[5] += g.blackMaterial()[last];
            sums[6] += g.pieces()[0];
            sums[7] += Arrays.stream(g.pieces()).average().orElse(0);
            sums[8] += g.pieces()[last];
        }
        int n = Math.max(1, games.size());
        return new Material(sums[0] / n, sums[1] / n, sums[2] / n, sums[3] / n, sums[4] / n, sums[5] / n, sums[6] / n,
                sums[7] / n, sums[8] / n);
    }

    static List<PieceStats> pieces(Variant variant, List<GameResult> games) {
        int n = Math.max(1, games.size());
        long allMoves = games.stream().mapToLong(g -> g.moved().values().stream().mapToLong(Integer::longValue).sum()).sum();
        List<PieceStats> out = new ArrayList<>();
        for (PieceType type : variant.pieces()) {
            char c = type.letter();
            double start = 0;
            double end = 0;
            double onBoard = 0;
            double moves = 0;
            double took = 0;
            double lost = 0;
            double promoted = 0;
            for (GameResult g : games) {
                start += g.start().getOrDefault(c, 0);
                end += g.end().getOrDefault(c, 0);
                onBoard += g.counts().getOrDefault(c, 0L) / (double) g.whiteMaterial().length;
                moves += g.moved().getOrDefault(c, 0);
                took += g.took().getOrDefault(c, 0);
                lost += g.lost().getOrDefault(c, 0);
                promoted += g.promoted().getOrDefault(c, 0);
            }
            // survival: the share of those that stood (at the start or by promotion) still there at the end
            double survival = start + promoted == 0 ? 0 : Math.min(1, end / (start + promoted));
            out.add(new PieceStats(String.valueOf(c), type.name(), type.value(), type.royal(), start / n, end / n,
                    onBoard / n, moves / n, allMoves == 0 ? 0 : moves / allMoves, took / n, lost / n, promoted / n,
                    survival));
        }
        return out;
    }

    static Checks checks(List<GameResult> games) {
        int white = games.stream().mapToInt(GameResult::whiteChecked).sum();
        int black = games.stream().mapToInt(GameResult::blackChecked).sum();
        int n = Math.max(1, games.size());
        return new Checks(white, black, white / (double) n, black / (double) n, count(games, g -> g.whiteChecked() > 0),
                count(games, g -> g.blackChecked() > 0));
    }

    /** A few games worth replaying: the shortest decisive one, the longest, and one of each result. */
    static List<SampleGame> samples(List<GameResult> games) {
        Map<String, GameResult> picked = new LinkedHashMap<>();
        games.stream().filter(GameResult::decisive).min((a, b) -> a.plies() - b.plies())
                .ifPresent(g -> picked.put("Shortest decisive game", g));
        games.stream().max((a, b) -> a.plies() - b.plies()).ifPresent(g -> picked.put("Longest game", g));
        games.stream().filter(g -> g.result().equals("1-0")).findFirst().ifPresent(g -> picked.put("A White win", g));
        games.stream().filter(g -> g.result().equals("0-1")).findFirst().ifPresent(g -> picked.put("A Black win", g));
        games.stream().filter(g -> !g.decisive()).findFirst().ifPresent(g -> picked.put("A draw", g));
        Set<Integer> seen = new LinkedHashSet<>();
        List<SampleGame> out = new ArrayList<>();
        picked.forEach((label, g) -> {
            if (seen.add(g.index())) {
                out.add(new SampleGame(label, g.index(), g.result(), g.ending(), g.plies(), g.uci(), g.san()));
            }
        });
        return out;
    }

    private static int count(List<GameResult> games, Predicate<GameResult> test) {
        return (int) games.stream().filter(test).count();
    }

    /** What a player would notice: one side favoured, nothing decided, over at once, or no choices. */
    static List<String> notes(int games, int white, int black, int draws, double averagePlies, double movesPerTurn,
                              int capped) {
        List<String> notes = new ArrayList<>();
        if (games == 0) {
            return notes;
        }
        double whiteScore = (white + draws / 2.0) / games;
        if (games >= 10 && whiteScore >= 0.65) {
            notes.add(String.format("White scores %.0f%%: the first move may be too strong.", whiteScore * 100));
        } else if (games >= 10 && whiteScore <= 0.35) {
            notes.add(String.format("Black scores %.0f%%: moving first may be a disadvantage.", (1 - whiteScore) * 100));
        }
        if (draws >= 0.7 * games) {
            notes.add(String.format("%.0f%% draws: games rarely get decided.", 100.0 * draws / games));
        }
        if (capped >= 0.3 * games) {
            notes.add(String.format("%.0f%% of the games were still going after the move limit.", 100.0 * capped / games));
        }
        if (averagePlies < 20) {
            notes.add(String.format("Games last %.0f moves on average: they may end too soon.", averagePlies / 2));
        }
        if (movesPerTurn < 8) {
            notes.add(String.format("Only %.1f moves to choose from per turn on average.", movesPerTurn));
        }
        return notes;
    }
}
