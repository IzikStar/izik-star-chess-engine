package lab;

import ai.variant.Variant;
import arena.GameRecord;
import arena.Opening;
import arena.Player;
import arena.Players;
import arena.Tournament;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Training positions from any game: a player (Fairy-Stockfish, a champion's weights, the random
 * mover...) plays itself from random openings, and the games' positions are written as
 * {@link TrainingExport} writes a run's ({@code fen,result}). The raw material for fitting weights
 * in a variant, or for training a network (docs/phase-7-research.md), whatever the network ends up
 * looking like.
 */
public final class SelfPlayData {

    private SelfPlayData() {}

    /**
     * Plays {@code games} games of {@code variant}, {@code spec} (as {@link Players} names it, at
     * {@code depth} for the built-in engine) against itself, each from its own opening of
     * {@code openingPlies} random moves, and writes their positions. Returns the lines written.
     */
    public static int play(Variant variant, String spec, int depth, int games, int openingPlies, int threads,
                           long seed, Writer out, Consumer<String> progress) throws IOException {
        if (games < 1) {
            throw new IllegalArgumentException("at least one game");
        }
        Player white = Players.parse(spec, "white", depth, 20, Players.HALL_OF_FAME, variant);
        Player black = Players.parse(spec, "black", depth, 20, Players.HALL_OF_FAME, variant);
        List<Opening> openings = Opening.random(variant, games, openingPlies, seed);
        List<Tournament.Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            // fewer distinct openings than games (a tiny opening tree): reuse them, with other seeds
            fixtures.add(new Tournament.Fixture(white, black, openings.get(i % openings.size()), seed * 1_000_003L + i));
        }
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        List<GameRecord> records = Tournament.play(fixtures,
                new Tournament.Settings(arena.Match.DEFAULT_MAX_PLIES, threads, seed, variant), g -> {
                    int n = done.incrementAndGet();
                    if (n % 100 == 0) {
                        progress.accept(n + "/" + games + " games");
                    }
                });
        return TrainingExport.write(variant, records, out);
    }
}
