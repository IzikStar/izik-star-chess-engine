package characterization;

import ai.BoardState;
import ai.Minimax;
import ai.BitBoard.BitMove;
import main.Move;
import rules.ChessMove;
import rules.GameStatus;
import rules.Rules;

/** Helpers for driving the minimax search directly, outside the Swing app. */
final class SearchTestSupport {

    private SearchTestSupport() {}

    /** Mate in one for White: Ra8# (Rb8# also mates). */
    static final String WHITE_MATES_IN_ONE = "7k/1R6/6K1/8/8/8/8/R7 w - - 0 1";

    /** The colour-mirror of {@link #WHITE_MATES_IN_ONE}: Ra1# (Rb1# also mates) for Black. */
    static final String BLACK_MATES_IN_ONE = "r7/8/8/8/8/6k1/1r6/7K b - - 0 1";

    /** Runs the engine's search on {@code fen} at {@code depth} and returns its move. */
    static ChessMove bestMove(String fen, int depth) {
        BoardState state = new BoardState(fen, null);
        Minimax.maxDepth = depth;
        BitMove bitMove = Minimax.getBestMove(state);
        Move move = new Move(state, bitMove);
        int from = move.piece.row * 8 + move.piece.col;
        int to = move.newRow * 8 + move.newCol;
        return new ChessMove(from, to, (char) 0);
    }

    static GameStatus statusAfter(String fen, ChessMove move) {
        return Rules.status(Rules.applyMove(fen, move));
    }
}
