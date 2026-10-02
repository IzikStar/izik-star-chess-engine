package arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Plays many arena games at once. Every pairing plays every opening twice, once with each player
 * as White, so neither gets the better side of an opening.
 */
public final class Tournament {

    /** Two players who meet. Their names must differ: results are counted by name. */
    public record Pairing(Player a, Player b) {
        public Pairing {
            if (a.name().equals(b.name())) {
                throw new IllegalArgumentException("both players are called " + a.name());
            }
        }
    }

    /**
     * @param maxPlies a game still going after this many plies is a draw
     * @param threads  games played at once
     * @param seed     with the same pairings and openings, the same seed gives the same games
     */
    public record Settings(int maxPlies, int threads, long seed) {
        public static Settings defaults() {
            return new Settings(Match.DEFAULT_MAX_PLIES, Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 1);
        }
    }

    private Tournament() {}

    /** One pairing over {@code openings}: {@code 2 × openings.size()} games. */
    public static List<GameRecord> match(Player a, Player b, List<Opening> openings, Settings settings,
                                         Consumer<GameRecord> onGame) {
        return run(List.of(new Pairing(a, b)), openings, settings, onGame);
    }

    /**
     * Plays every pairing over every opening with both colours, {@code settings.threads()} games at
     * a time. {@code onGame} hears of each game as it ends (from the playing threads, in any order);
     * the returned list is in a fixed order: pairing, then opening, then {@code a} as White first.
     */
    public static List<GameRecord> run(List<Pairing> pairings, List<Opening> openings, Settings settings,
                                       Consumer<GameRecord> onGame) {
        ExecutorService pool = Executors.newFixedThreadPool(settings.threads());
        try {
            List<Future<GameRecord>> games = new ArrayList<>();
            long index = 0;
            for (Pairing pairing : pairings) {
                for (Opening opening : openings) {
                    for (boolean aIsWhite : new boolean[]{true, false}) {
                        Player white = aIsWhite ? pairing.a() : pairing.b();
                        Player black = aIsWhite ? pairing.b() : pairing.a();
                        long seed = settings.seed() * 1_000_003L + index++;
                        games.add(pool.submit(() -> {
                            GameRecord game = Match.play(white, black, opening, settings.maxPlies(), seed);
                            onGame.accept(game);
                            return game;
                        }));
                    }
                }
            }
            List<GameRecord> results = new ArrayList<>();
            for (Future<GameRecord> game : games) {
                results.add(game.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("a game failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
