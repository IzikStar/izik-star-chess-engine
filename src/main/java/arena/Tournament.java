package arena;

import ai.variant.Variant;
import ai.variant.Variants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
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
     * @param variant  the game the players play
     */
    public record Settings(int maxPlies, int threads, long seed, Variant variant) {

        /** Chess games. */
        public Settings(int maxPlies, int threads, long seed) {
            this(maxPlies, threads, seed, Variants.CHESS);
        }

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

    /** One game to play: who has White, who has Black, from which opening, with which seed. */
    public record Fixture(Player white, Player black, Opening opening, long seed) {}

    /**
     * Plays every pairing over every opening with both colours, {@code settings.threads()} games at
     * a time. {@code onGame} hears of each game as it ends (from the playing threads, in any order);
     * the returned list is in a fixed order: pairing, then opening, then {@code a} as White first.
     */
    public static List<GameRecord> run(List<Pairing> pairings, List<Opening> openings, Settings settings,
                                       Consumer<GameRecord> onGame) {
        List<Fixture> fixtures = new ArrayList<>();
        for (Pairing pairing : pairings) {
            for (Opening opening : openings) {
                fixtures.add(new Fixture(pairing.a(), pairing.b(), opening, 0));
                fixtures.add(new Fixture(pairing.b(), pairing.a(), opening, 0));
            }
        }
        List<Fixture> seeded = new ArrayList<>();
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture f = fixtures.get(i);
            seeded.add(new Fixture(f.white(), f.black(), f.opening(), settings.seed() * 1_000_003L + i));
        }
        return play(seeded, settings, onGame);
    }

    /**
     * Plays {@code fixtures}, {@code settings.threads()} at a time (the settings' seed is not used:
     * each fixture has its own). The returned list is in the fixtures' order.
     */
    public static List<GameRecord> play(List<Fixture> fixtures, Settings settings, Consumer<GameRecord> onGame) {
        return play(fixtures, settings, onGame, () -> false);
    }

    /** Thrown by {@link #play(List, Settings, Consumer, BooleanSupplier)} when it was cancelled. */
    public static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("the games were cancelled");
        }
    }

    /**
     * As {@link #play(List, Settings, Consumer)}, giving up (with {@link Cancelled}) as soon as
     * {@code cancel} says so: games not started are dropped, games under way finish.
     */
    public static List<GameRecord> play(List<Fixture> fixtures, Settings settings, Consumer<GameRecord> onGame,
                                        BooleanSupplier cancel) {
        ExecutorService pool = Executors.newFixedThreadPool(settings.threads());
        try {
            List<Future<GameRecord>> games = new ArrayList<>();
            for (Fixture f : fixtures) {
                games.add(pool.submit(() -> {
                    if (cancel.getAsBoolean()) {
                        throw new Cancelled();
                    }
                    GameRecord game = Match.play(settings.variant(), f.white(), f.black(), f.opening(), settings.maxPlies(), f.seed());
                    onGame.accept(game);
                    return game;
                }));
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
            if (e.getCause() instanceof Cancelled c) {
                throw c;
            }
            throw new IllegalStateException("a game failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
