package lab;

import ai.Minimax;
import ai.variant.Variant;
import engine.MinimaxEngine;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * The variant health check (Phase 6 R5d): the built-in engine plays a variant against itself and
 * the games say whether the variant is any good to play. Does one side win too often? Is it a
 * draw every time, over too soon, or never over? How many moves does a player have to choose from?
 *
 * <p>Every game starts with a few random moves, so the games differ, then both sides search to the
 * same depth with a little variety. A game still going after {@link Settings#maxPlies} is stopped
 * and counted as a draw ({@code PLY_CAP}). The same settings always give the same games.
 */
public final class VariantHealth {

    private VariantHealth() {}

    /**
     * @param games        how many games to play
     * @param depth        the search depth of both sides
     * @param randomPlies  random moves at the start of each game
     * @param maxPlies     a game this long is stopped and scored as a draw
     * @param seed         the games' randomness
     */
    public record Settings(int games, int depth, int randomPlies, int maxPlies, long seed) {

        public static final int MAX_GAMES = 400;
        public static final int MAX_DEPTH = 4;

        public Settings {
            if (games < 1 || games > MAX_GAMES) {
                throw new IllegalArgumentException("games is 1 to " + MAX_GAMES + ": " + games);
            }
            if (depth < 1 || depth > MAX_DEPTH) {
                throw new IllegalArgumentException("depth is 1 to " + MAX_DEPTH + ": " + depth);
            }
            if (randomPlies < 0 || maxPlies < 10) {
                throw new IllegalArgumentException("randomPlies >= 0 and maxPlies >= 10");
            }
        }

        public static Settings of(int games, int depth) {
            return new Settings(games, depth, 4, 300, 2026);
        }
    }

    /** One game: how it ended, after how many plies, and how many legal moves there were along the way. */
    public record GameResult(String result, String ending, int plies, long legalMoves) {}

    /**
     * What the games say.
     *
     * @param endings      how the games ended, by {@link GameStatus} name or {@code PLY_CAP}
     * @param movesPerTurn the average number of legal moves a player had to choose from
     * @param notes        plain words on what looks wrong (one side wins too often, all draws...)
     */
    public record Report(int games, int whiteWins, int blackWins, int draws, double averagePlies, int shortest,
                         int longest, double movesPerTurn, Map<String, Integer> endings, List<String> notes) {

        public double whiteScore() {
            return games == 0 ? 0 : (whiteWins + draws / 2.0) / games;
        }

        public double decisiveShare() {
            return games == 0 ? 0 : (whiteWins + blackWins) / (double) games;
        }
    }

    /** Plays the games on every core but one; {@code progress} hears each finished game's count. */
    public static Report run(Variant variant, Settings settings, IntConsumer progress, BooleanSupplier cancel) {
        int threads = Math.max(1, Math.min(settings.games(), Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "variant-health");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<GameResult>> futures = new ArrayList<>();
            for (int i = 0; i < settings.games(); i++) {
                long seed = settings.seed() * 1_000_003L + i;
                futures.add(pool.submit(() -> {
                    GameResult r = play(variant, settings, seed, cancel);
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
            return report(results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /** One game of the engine against itself; {@code null} if cancelled. */
    static GameResult play(Variant variant, Settings settings, long seed, BooleanSupplier cancel) {
        Random random = new Random(seed);
        Minimax.Options options = new Minimax.Options(MinimaxEngine.DEFAULT_VARIETY, random, true);
        Game game = new Game(variant);
        long legalMoves = 0;
        while (!game.status().isGameOver() && game.plyCount() < settings.maxPlies()) {
            if (cancel.getAsBoolean()) {
                return null;
            }
            List<ChessMove> legal = game.legalMoves();
            legalMoves += legal.size();
            ChessMove move = game.plyCount() < settings.randomPlies()
                    ? legal.get(random.nextInt(legal.size()))
                    : MinimaxEngine.searchAtDepth(variant, game.history(), settings.depth(), options, cancel);
            if (move == null) {
                return null; // cancelled; a position with no legal move is game over above
            }
            game.play(move);
        }
        GameStatus status = game.status();
        boolean whiteToMove = game.fen().split(" ")[1].equals("w");
        String result = status.isGameOver() ? status.result(whiteToMove) : "1/2-1/2";
        return new GameResult(result, status.isGameOver() ? status.name() : "PLY_CAP", game.plyCount(), legalMoves);
    }

    static Report report(List<GameResult> results) {
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
        return new Report(n, white, black, draws, averagePlies, n == 0 ? 0 : shortest, longest, movesPerTurn, endings, notes);
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
