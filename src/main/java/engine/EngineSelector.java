package engine;

import rules.ChessMove;

/**
 * Chooses which engine plays at a given level, with a fallback: levels at or above
 * {@link #STOCKFISH_FROM_LEVEL} ask Stockfish, and if it is missing or gives no legal move the
 * built-in engine answers at {@code fallbackLevel} (the behaviour {@code main.Input} hard-coded).
 */
public final class EngineSelector {

    /** The UI's levels 8-10 (skill 14, 16, 18) use Stockfish. */
    public static final int STOCKFISH_FROM_LEVEL = 13;

    private final Engine builtIn;
    private final Engine stockfish;

    public EngineSelector(Engine builtIn, Engine stockfish) {
        this.builtIn = builtIn;
        this.stockfish = stockfish;
    }

    /** The engine's move at {@code skillLevel}. */
    public ChessMove move(String fen, int skillLevel) {
        if (skillLevel < STOCKFISH_FROM_LEVEL) {
            return builtIn.bestMove(fen, skillLevel);
        }
        return withFallback(fen, skillLevel, 3);
    }

    /** A hint for the side to move: Stockfish at full strength, else the built-in engine at 10. */
    public ChessMove hint(String fen) {
        return withFallback(fen, 21, 10);
    }

    private ChessMove withFallback(String fen, int stockfishLevel, int fallbackLevel) {
        ChessMove move = stockfish.isAvailable() ? stockfish.bestMove(fen, stockfishLevel) : null;
        return move != null ? move : builtIn.bestMove(fen, fallbackLevel);
    }

    public void close() {
        builtIn.close();
        stockfish.close();
    }
}
