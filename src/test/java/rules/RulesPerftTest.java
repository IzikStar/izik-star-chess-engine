package rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions.Position;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft through the rules facade, {@link Rules#legalMoves} and {@link Rules#applyMove}: the moves a
 * player is offered, with every position re-read from FEN, so castling rights and en-passant
 * squares must also survive the FEN round trip (docs/phase-4b-research.md §2).
 */
class RulesPerftTest {

    /** Each position runs to the deepest depth within this many nodes, about a second in all. */
    private static final long QUICK_NODES = 100_000;

    static long perft(String fen, int depth) {
        if (depth == 1) {
            return Rules.legalMoves(fen).size();
        }
        long nodes = 0;
        for (ChessMove move : Rules.legalMoves(fen)) {
            nodes += perft(Rules.applyMove(fen, move), depth - 1);
        }
        return nodes;
    }

    static Stream<Position> positions() {
        return PerftPositions.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("The game offers exactly the legal moves")
    void gameViewMatchesReference(Position p) {
        int depth = p.depthWithin(QUICK_NODES);
        assertEquals(p.count(depth), perft(p.fen(), depth), p + ", depth " + depth);
    }
}
