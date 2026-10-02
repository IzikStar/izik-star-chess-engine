package engine;

import rules.ChessMove;

/**
 * Chooses which engine plays at a given level, with a fallback: levels at or above
 * {@link #STOCKFISH_FROM_LEVEL} ask Stockfish, and if it is missing or gives no legal move the
 * built-in engine answers at a lower level (the behaviour {@code main.Input} hard-coded).
 */
public final class EngineSelector {

    /** The UI's levels 8-10 (skill 14, 16, 18) use Stockfish. */
    public static final int STOCKFISH_FROM_LEVEL = 13;
    /** Stockfish's level for hints (Skill Level 20, its full strength). */
    public static final int HINT_LEVEL = 21;
    static final int MOVE_FALLBACK_LEVEL = 3;
    static final int HINT_FALLBACK_LEVEL = 10;

    private final Engine builtIn;
    private final Engine stockfish;

    public EngineSelector(Engine builtIn, Engine stockfish) {
        this.builtIn = builtIn;
        this.stockfish = stockfish;
    }

    /** The engine's move at {@code skillLevel}, for a bare position. */
    public ChessMove move(String fen, int skillLevel) {
        return move(SearchRequest.of(fen, skillLevel));
    }

    /** The engine's move at the request's level. */
    public ChessMove move(SearchRequest request) {
        if (request.skillLevel() < STOCKFISH_FROM_LEVEL) {
            return builtIn.bestMove(request);
        }
        return withFallback(request, MOVE_FALLBACK_LEVEL);
    }

    /** A hint for the side to move, for a bare position. */
    public ChessMove hint(String fen) {
        return hint(SearchRequest.of(fen, HINT_LEVEL));
    }

    /** A hint for the side to move: Stockfish at full strength, else the built-in engine at 10. */
    public ChessMove hint(SearchRequest request) {
        return withFallback(request.withSkillLevel(HINT_LEVEL), HINT_FALLBACK_LEVEL);
    }

    /** A quick move from the built-in engine, for when a search failed outright. */
    public ChessMove quickMove(SearchRequest request) {
        return builtIn.bestMove(request.withSkillLevel(MOVE_FALLBACK_LEVEL));
    }

    private ChessMove withFallback(SearchRequest request, int fallbackLevel) {
        ChessMove move = stockfish.isAvailable() ? stockfish.bestMove(request) : null;
        if (move != null || request.cancel().isCancelled()) {
            return move;
        }
        return builtIn.bestMove(request.withSkillLevel(fallbackLevel));
    }

    public void close() {
        builtIn.close();
        stockfish.close();
    }
}
