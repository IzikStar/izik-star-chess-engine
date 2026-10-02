package game;

import rules.ChessMove;
import rules.MoveResult;

/**
 * What a front end hears from a {@link GameSession}. All callbacks arrive on the session's
 * dispatcher thread ({@code web.GameHub}'s game thread). Default methods, so a listener
 * implements only what it needs.
 */
public interface GameListener {

    /** A move was played (by a human or the engine). */
    default void moveMade(MoveResult move, boolean byEngine) {
    }

    /** The game just ended; {@code lastMove} is the move that ended it. */
    default void gameOver(MoveResult lastMove) {
    }

    /** The position changed without a move: new game, take-back, or loaded position. */
    default void positionReset() {
    }

    /** The configuration changed (mode, colour, level). */
    default void configChanged(GameConfig config) {
    }

    /** The engine's suggestion for the side to move. */
    default void hint(ChessMove move) {
    }
}
