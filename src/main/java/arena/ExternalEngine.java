package arena;

import engine.StockfishLocator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An engine outside this program that speaks UCI, e.g. Stockfish, as an arena player.
 *
 * <p>Each move is limited by nodes (positions searched) rather than time, so a game does not
 * depend on how busy the machine is. Stockfish held to a UCI_Elo still picks among its candidate
 * moves at random, so its games are not repeatable the way the built-in engine's are.
 *
 * @param command    the executable
 * @param options    UCI options sent before the game ({@code setoption name K value V})
 * @param nodes      positions searched per move ({@code go nodes N}); 0 uses {@code moveMillis}
 * @param moveMillis time per move ({@code go movetime N}) when {@code nodes} is 0
 */
public record ExternalEngine(String command, Map<String, String> options, long nodes, long moveMillis) {

    /** Nodes per Stockfish move in the arena: fast enough to keep up with a depth-3 player. */
    public static final long STOCKFISH_NODES = 20_000;
    /** Lowest and highest strength Stockfish can be held to. */
    public static final int STOCKFISH_MIN_ELO = 1320, STOCKFISH_MAX_ELO = 3190;

    public ExternalEngine {
        options = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(options));
        if (nodes <= 0 && moveMillis <= 0) {
            throw new IllegalArgumentException("an external engine needs a node or time limit per move");
        }
    }

    /** Stockfish held to {@code elo} (UCI_Elo, 1320-3190), {@link #STOCKFISH_NODES} a move, one thread. */
    public static ExternalEngine stockfish(int elo) {
        return stockfish(elo, STOCKFISH_NODES);
    }

    public static ExternalEngine stockfish(int elo, long nodes) {
        if (elo < STOCKFISH_MIN_ELO || elo > STOCKFISH_MAX_ELO) {
            throw new IllegalArgumentException("Stockfish's UCI_Elo goes from " + STOCKFISH_MIN_ELO + " to "
                    + STOCKFISH_MAX_ELO + ", not " + elo);
        }
        Path executable = StockfishLocator.find().orElseThrow(() ->
                new IllegalStateException("Stockfish not found: install it, or download it from the game"));
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Threads", "1");
        options.put("Hash", "16");
        options.put("UCI_LimitStrength", "true");
        options.put("UCI_Elo", String.valueOf(elo));
        return new ExternalEngine(executable.toString(), options, nodes, 0);
    }
}
