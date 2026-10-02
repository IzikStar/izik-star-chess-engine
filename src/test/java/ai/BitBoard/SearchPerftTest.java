package ai.BitBoard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions;
import rules.PerftPositions.Position;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft through the search's own move generator, {@link BitBoard#getNextStates()}: the boards
 * {@code Minimax} walks (docs/phase-4b-research.md §2). Every count must match the reference.
 */
@Tag("known-bug")
class SearchPerftTest {

    /** Each position runs to the deepest depth within this many nodes, about a second in all. */
    private static final long QUICK_NODES = 300_000;

    static long perft(BitBoard board, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (BitBoard child : board.getNextStates()) {
            nodes += perft(child, depth - 1);
            child.nextStates = null; // let the explored subtree be collected
        }
        return nodes;
    }

    static Stream<Position> positions() {
        return PerftPositions.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("The search generates exactly the legal moves")
    void searchViewMatchesReference(Position p) {
        int depth = p.depthWithin(QUICK_NODES);
        assertEquals(p.count(depth), perft(BitBoardRules.fromFen(p.fen()), depth), p + ", depth " + depth);
    }
}
