package engine;

import rules.ChessMove;

/**
 * Chooses which engine plays at a given level ({@link Levels}), with a fallback: Stockfish's levels
 * ask Stockfish, and if it is missing or gives no legal move the built-in engine answers at its
 * strongest level. (Until October 2026 the fallback was a weak level, so a missing Stockfish turned
 * the top level into one of the weakest opponents, and nothing said so.)
 */
public final class EngineSelector {

    /** Levels from here up use Stockfish. */
    public static final int STOCKFISH_FROM_LEVEL = Levels.STOCKFISH_FROM;
    /** Stockfish's level for hints: its full strength. */
    public static final int HINT_LEVEL = Levels.HINT;
    /** The built-in engine's strongest level: what Stockfish's levels play without Stockfish. */
    static final int MOVE_FALLBACK_LEVEL = Levels.TOP_BUILT_IN;
    /** A quick move after a search failed outright (depth 1). */
    static final int QUICK_MOVE_LEVEL = 2;
    /** Hints without Stockfish: the built-in engine at depth 5. */
    static final int HINT_FALLBACK_LEVEL = 6;

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
        return builtIn.bestMove(request.withSkillLevel(QUICK_MOVE_LEVEL));
    }

    /** Whether Stockfish can play: found, and not given up on after repeated failures. */
    public boolean stockfishAvailable() {
        return stockfish.isAvailable();
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
