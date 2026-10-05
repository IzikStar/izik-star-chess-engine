package ai;

import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitBoardRules;
import ai.board.BoardRules;
import ai.board.GenericBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 6 R3: the search finds the same value on the generic board as on the bitboard, at every
 * depth up to 4, with and without the transposition table and move ordering. The boards list moves
 * in a different order, so where several moves share the best value the search may pick another.
 */
class GenericSearchTest {

    static Stream<PerftPositions.Position> positions() {
        return Stream.concat(PerftPositions.STANDARD.stream(), PerftPositions.EDGE.stream());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("Same search value at depths 1-4")
    void sameValue(PerftPositions.Position p) {
        for (boolean speedups : new boolean[]{false, true}) {
            Minimax.Options options = Minimax.Options.DEFAULT.withSpeedups(speedups);
            for (int depth = 1; depth <= 4; depth++) {
                int old = Minimax.searchValue(BitBoardRules.fromFen(p.fen()), depth, BitBoardEvaluate.DEFAULT, options);
                int now = Minimax.searchValue(GenericBoard.fromFen(BoardRules.CHESS, p.fen()), depth,
                        BitBoardEvaluate.DEFAULT, options);
                assertEquals(old, now, p.name() + " depth " + depth + (speedups ? " with" : " without") + " speedups");
            }
        }
    }
}
