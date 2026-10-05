package ai.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions;
import rules.PerftPositions.Position;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft through the search's own move generator, {@link Board#children()}: the boards
 * {@code Minimax} walks (docs/phase-4b-research.md §2). Every count must match the reference.
 * {@code mvn test} runs each position to a few hundred thousand nodes; {@code -Pstress} runs the
 * deepest count known, up to several million nodes.
 */
class SearchPerftTest {

    /** Each position runs to the deepest depth within this many nodes, about a second in all. */
    private static final long QUICK_NODES = 300_000;

    static Stream<Position> positions() {
        return PerftPositions.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("The search generates exactly the legal moves")
    void searchViewMatchesReference(Position p) {
        int depth = p.depthWithin(QUICK_NODES);
        assertEquals(p.count(depth), SearchBoards.perft(Boards.fromFen(p.fen()), depth), p + ", depth " + depth);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @Tag("stress")
    @DisplayName("The search generates exactly the legal moves, to the deepest reference count")
    void searchViewMatchesReferenceAtFullDepth(Position p) {
        int depth = p.maxDepth();
        assertEquals(p.count(depth), SearchBoards.perft(Boards.fromFen(p.fen()), depth), p + ", depth " + depth);
    }
}
