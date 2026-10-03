package engine;

import ai.BitBoard.BitBoardEvaluate;
import ai.Minimax;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;
import rules.Position;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The search knows the game behind the position: moving back into a position the game already had
 * is scored as a draw, so a side that is ahead avoids it and a side that is behind goes for it.
 * Before, the search saw no game history, and a queen up the engine drew by threefold repetition.
 */
class RepetitionTest {

    /** White: king e1, queen b2; Black: king f7. */
    private static final String QUEEN_UP = "8/5k2/8/8/8/8/1Q6/4K3 w - - 0 1";

    private static Game played(String... uci) {
        Game game = new Game(QUEEN_UP);
        for (String move : uci) {
            game.play(move);
        }
        return game;
    }

    private static ChessMove search(Game game, int depth) {
        return MinimaxEngine.searchAtDepth(game.fen(), game.history(), depth, BitBoardEvaluate.DEFAULT,
                Minimax.Options.DEFAULT);
    }

    @Test
    @DisplayName("A queen up, the engine does not move back into a position the game already had")
    void aheadAvoidsRepeating() {
        Game game = played("b2b8", "f7e7", "b8b2", "e7f7"); // back to the start
        assertEquals("b2b8", MinimaxEngine.searchAtDepth(game.fen(), 5).toUci(), "without the game it repeats");
        assertNotEquals("b2b8", search(game, 5).toUci());
    }

    @Test
    @DisplayName("A queen down, the engine goes back into a position the game already had")
    void behindRepeats() {
        Game game = played("b2b8", "f7e7", "b8b2"); // Black to move; e7f7 is the start again
        assertEquals("e7f7", search(game, 5).toUci());
    }

    @Test
    @DisplayName("Level 4 with a queen up mates instead of drawing by repetition")
    void winsTheGame() {
        Game game = new Game(QUEEN_UP);
        MinimaxEngine white = new MinimaxEngine(new Random(1), MinimaxEngine.TIME_CAP_MS, BitBoardEvaluate.DEFAULT, 0);
        MinimaxEngine black = new MinimaxEngine(new Random(2), MinimaxEngine.TIME_CAP_MS, BitBoardEvaluate.DEFAULT, 0);
        while (!game.status().isGameOver() && game.plyCount() < 100) {
            boolean whiteToMove = game.fen().split(" ")[1].equals("w");
            List<ChessMove> moves = game.moves().stream().map(MoveResult::move).toList();
            SearchRequest request = new SearchRequest(game.fen(), QUEEN_UP, moves, whiteToMove ? 4 : 3,
                    Cancellation.NONE);
            game.play((whiteToMove ? white : black).bestMove(request));
        }
        assertEquals(GameStatus.CHECKMATE, game.status());
    }

    @Test
    @DisplayName("Only positions since the last capture or pawn move are passed to the search")
    void historyStopsAtIrreversibleMoves() {
        List<String> fens = MinimaxEngine.gameFens(Position.START_FEN,
                List.of(ChessMove.fromUci("e2e4"), ChessMove.fromUci("e7e5"), ChessMove.fromUci("g1f3")));
        String current = fens.getLast(); // half-move clock 1: only the position after e7e5 can recur
        assertEquals(1, MinimaxEngine.gameHistory(fens.subList(0, 3), current).length);
        assertEquals(1, MinimaxEngine.gameHistory(fens, current).length, "the current position is left out");
    }
}
