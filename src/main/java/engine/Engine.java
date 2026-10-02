package engine;

import rules.ChessMove;

/**
 * A chess engine: given a position, choose a move for the side to move. Engines are pure move
 * pickers — they never touch the board, the UI or any global settings; the caller applies the
 * move (Phase 3, see docs/phase-3-research.md Fork 4). Each engine bounds its own thinking time
 * and gives up soon after the request is cancelled (Phase 4).
 */
public interface Engine {

    /**
     * Best move for the side to move in {@code request.fen()}, or {@code null} if this engine
     * could not produce a legal move or the request was cancelled.
     */
    ChessMove bestMove(SearchRequest request);

    /** {@link #bestMove(SearchRequest)} for a bare position that nobody will cancel. */
    default ChessMove bestMove(String fen, int skillLevel) {
        return bestMove(SearchRequest.of(fen, skillLevel));
    }

    /** False when the engine cannot run at all (e.g. the Stockfish executable is missing). */
    default boolean isAvailable() {
        return true;
    }

    /** Releases any external resources (processes, threads). */
    default void close() {
    }
}
