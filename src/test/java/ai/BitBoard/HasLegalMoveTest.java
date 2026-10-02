package ai.BitBoard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions.Position;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BitBoard#hasLegalMove()} stops at the first legal move instead of building every child,
 * so it repeats the move rules in short form. It must agree with the full move list everywhere,
 * mates and stalemates included.
 */
class HasLegalMoveTest {

    private static final long NODES = 20_000;

    private static void walk(BitBoard board, int depth, String path) {
        boolean early = board.hasLegalMove(); // before the children are built and cached
        assertEquals(!board.getNextStates().isEmpty(), early, "after: " + path);
        if (depth > 0) {
            for (BitBoard child : board.getNextStates()) {
                walk(child, depth - 1, path + " " + SearchBoards.uci(board, child));
                child.nextStates = null;
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules.PerftPositions#all")
    @DisplayName("Whether a legal move exists agrees with the full move list")
    void agreesWithTheMoveList(Position p) {
        walk(BitBoardRules.fromFen(p.fen()), p.depthWithin(NODES), p.fen() + " moves");
    }
}
