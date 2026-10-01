package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.GameStatus;
import rules.Rules;

import static characterization.SearchTestSupport.WHITE_MATES_IN_ONE;
import static characterization.SearchTestSupport.bestMove;
import static characterization.SearchTestSupport.statusAfter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Search bugs reproduced during the Phase 3 research (docs/phase-3-research.md §1.2). These
 * assert the CORRECT behaviour and fail today; run with {@code mvn test -Pknown-bugs}. Phase 3
 * increment 2 is expected to turn them green and move them into {@link SearchTest}.
 */
@Tag("known-bug")
class SearchKnownBugsTest extends CharacterizationTestBase {

    /**
     * Bug B: {@code BitBoardEvaluate.evaluate} negates {@code Integer.MIN_VALUE} for a White
     * engine, which overflows, so delivering mate scores as the worst outcome. Today the engine
     * plays Kf7 here, walking away from the mate.
     */
    @Test
    @DisplayName("Engine as White finds a mate in one (depth 2)")
    void whiteEngineMatesInOne() {
        main.setting.ChoosePlayFormat.isPlayingWhite = false; // human Black, engine White
        ChessMove move = bestMove(WHITE_MATES_IN_ONE, 2);
        assertEquals(GameStatus.CHECKMATE, statusAfter(WHITE_MATES_IN_ONE, move),
                "engine played " + move.toUci());
    }

    /**
     * Bug A ("the engine stops playing"): the search decides whether it is at its root by
     * reading UI flags. When they disagree with the side to move (the flip-and-restore hacks
     * around async engine calls leave them like that), {@code Minimax.bestMoves} is never
     * initialised and {@code getBestMove} throws a NullPointerException that the app swallows.
     */
    @Test
    @DisplayName("Search for the side to move never depends on the UI's idea of who the human is")
    void searchIgnoresUiFlags() {
        // stock settings: human White. Ask for a White move anyway, as a hint or fallback does.
        // Start from a fresh JVM's state: otherwise a stale list from an earlier search in this
        // run is reused and a move belonging to a different position comes back.
        ai.Minimax.bestMoves = null;
        ChessMove move = assertDoesNotThrow(() -> bestMove(WHITE_MATES_IN_ONE, 2));
        assertTrue(Rules.isLegal(WHITE_MATES_IN_ONE, move), "engine played " + move.toUci());
    }
}
