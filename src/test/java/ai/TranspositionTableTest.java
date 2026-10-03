package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitBoardRules;
import ai.BitBoard.BitMove;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 5b transposition table and move ordering only make the search faster
 * (docs/phase-5b-research.md §5): at every depth it finds the same score as the search without them.
 */
class TranspositionTableTest {

    static final Minimax.Options OFF = Minimax.Options.DEFAULT.withSpeedups(false);
    static final Minimax.Options ON = Minimax.Options.DEFAULT.withSpeedups(true);

    /** The 56 positions of engine/same-moves.txt. */
    static List<String> positions() throws IOException {
        try (InputStream in = TranspositionTableTest.class.getResourceAsStream("/engine/same-moves.txt")) {
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split(" \\| ")[0])
                    .toList();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("Depths 1-4 find the same score with and without the table and the ordering")
    void sameScore(String fen) {
        for (int depth = 1; depth <= 4; depth++) {
            int before = Minimax.searchValue(BitBoardRules.fromFen(fen), depth, BitBoardEvaluate.DEFAULT, OFF);
            int after = Minimax.searchValue(BitBoardRules.fromFen(fen), depth, BitBoardEvaluate.DEFAULT, ON);
            assertEquals(before, after, "depth " + depth);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "7k/1R6/6K1/8/8/8/8/R7 w - - 0 1",           // mate in 1 (Ra8#)
            "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",       // back-rank mate in 1
            "r5k1/5ppp/8/8/8/8/5PPP/3R2K1 b - - 0 1",      // the side to move gets mated
            "8/8/8/8/8/5k2/8/4K2q w - - 0 1"})             // mated in a few
    @DisplayName("Mate scores read from the table keep their distance to the mate")
    void mateScores(String fen) {
        for (int depth = 1; depth <= 5; depth++) {
            int before = Minimax.searchValue(BitBoardRules.fromFen(fen), depth, BitBoardEvaluate.DEFAULT, OFF);
            int after = Minimax.searchValue(BitBoardRules.fromFen(fen), depth, BitBoardEvaluate.DEFAULT, ON);
            assertEquals(before, after, "depth " + depth);
        }
    }

    @Test
    @DisplayName("The table keeps the deeper entry and finds both entries of a slot pair")
    void replacement() {
        TranspositionTable table = new TranspositionTable(4);
        long a = 0x10, b = 0x100; // both land in slot pair 0 of a 16-slot table
        table.put(a, 5, 42, TranspositionTable.EXACT, 7);
        table.put(b, 2, -3, TranspositionTable.LOWER, 9);
        int sa = table.find(a);
        int sb = table.find(b);
        assertTrue(sa >= 0 && sb >= 0);
        assertNotEquals(sa, sb);
        assertEquals(42, table.value(sa));
        assertEquals(5, table.depth(sa));
        assertEquals(TranspositionTable.EXACT, table.bound(sa));
        assertEquals(7, table.move(sa));
        assertEquals(-3, table.value(sb));
        assertEquals(TranspositionTable.LOWER, table.bound(sb));
        table.put(a, 1, 0, TranspositionTable.UPPER, 0); // a shallower visit keeps the move it knew
        assertEquals(7, table.move(table.find(a)));
        assertEquals(-1, table.find(0x200));
    }

    @Test
    @DisplayName("Searches with the same seed pick the same moves, so arena games stay repeatable")
    void deterministic() {
        String fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5";
        BitMove first = Minimax.getBestMove(BitBoardRules.fromFen(fen), 4, BitBoardEvaluate.DEFAULT,
                new Minimax.Options(5, new Random(3), true), () -> false);
        BitMove second = Minimax.getBestMove(BitBoardRules.fromFen(fen), 4, BitBoardEvaluate.DEFAULT,
                new Minimax.Options(5, new Random(3), true), () -> false);
        assertEquals(Minimax.moveCode(first), Minimax.moveCode(second));
    }
}
