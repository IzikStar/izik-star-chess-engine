package ai.BitBoard;

import ai.Minimax;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A finished search keeps the root's moves, for the next depth, but not the tree below them
 * (docs/phase-4b-research.md §3.3 item 3). Keeping every searched board reachable is what took
 * gigabytes of heap at depth 5 and ran out of memory at depth 6.
 */
class SearchMemoryTest {

    @Test
    @DisplayName("After a search only the root's moves are kept, not the tree below them")
    void searchReleasesWhatItSearched() {
        BitBoard root = BitBoardRules.fromFen("r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5");
        assertNotNull(Minimax.getBestMove(root, 4));
        assertNotNull(root.nextStates, "the root keeps its moves");
        for (BitBoard child : root.nextStates) {
            assertNull(child.nextStates, "a searched move still holds its subtree");
        }
    }
}
