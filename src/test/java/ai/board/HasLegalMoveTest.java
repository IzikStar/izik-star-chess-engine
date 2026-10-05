package ai.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions.Position;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ChessPosition#hasLegalMove()} stops at the first legal move instead of building every
 * child. It must agree with the full move list everywhere, mates and stalemates included.
 */
class HasLegalMoveTest {

    private static final long NODES = 20_000;

    private static void walk(ChessPosition board, int depth, String path) {
        boolean early = board.hasLegalMove(); // before the children are built and cached
        assertEquals(!board.children().isEmpty(), early, "after: " + path);
        if (depth > 0) {
            for (Board child : board.children()) {
                walk((ChessPosition) child, depth - 1, path + " " + SearchBoards.uci(child));
            }
            board.releaseChildren();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules.PerftPositions#all")
    @DisplayName("Whether a legal move exists agrees with the full move list")
    void agreesWithTheMoveList(Position p) {
        walk((ChessPosition) Boards.fromFen(p.fen()), p.depthWithin(NODES), p.fen() + " moves");
    }
}
