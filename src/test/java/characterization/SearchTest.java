package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.GameStatus;
import rules.Rules;

import static characterization.SearchTestSupport.BLACK_MATES_IN_ONE;
import static characterization.SearchTestSupport.WHITE_MATES_IN_ONE;
import static characterization.SearchTestSupport.bestMove;
import static characterization.SearchTestSupport.statusAfter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 safety net for the minimax search (until Phase 3 untested). The last two cases were
 * {@code SearchKnownBugsTest} (red) until increment 2 — see docs/phase-3-research.md §1.2.
 */
class SearchTest extends CharacterizationTestBase {

    @Test
    @DisplayName("Engine as Black finds a mate in one (depth 2)")
    void blackEngineMatesInOne() {
        ChessMove move = bestMove(BLACK_MATES_IN_ONE, 2);
        assertEquals(GameStatus.CHECKMATE, statusAfter(BLACK_MATES_IN_ONE, move),
                "engine played " + move.toUci());
    }

    @Test
    @DisplayName("Engine as Black returns a legal move from the opening")
    void blackEngineLegalMoveFromOpening() {
        String afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        ChessMove move = bestMove(afterE4, 2);
        assertTrue(Rules.isLegal(afterE4, move), "engine played " + move.toUci());
    }

    @Test
    @DisplayName("Engine as White returns a legal move when the settings say the human is Black")
    void whiteEngineLegalMoveWhenHumanIsBlack() {
        main.setting.ChoosePlayFormat.isPlayingWhite = false;
        ChessMove move = bestMove(START, 2);
        assertTrue(Rules.isLegal(START, move), "engine played " + move.toUci());
    }

    /**
     * Was Bug B: {@code BitBoardEvaluate.evaluate} negated {@code Integer.MIN_VALUE} for a White
     * engine, which overflows, so delivering mate scored as the worst outcome (it played Kf7).
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
     * Was Bug A ("the engine stops playing"): the search decided whether it was at its root by
     * reading UI flags, and threw a NullPointerException when they disagreed with the side to
     * move. It now always searches for the side to move, whatever the UI settings say.
     */
    @Test
    @DisplayName("Search for the side to move does not depend on the UI's idea of who the human is")
    void searchIgnoresUiFlags() {
        // stock settings: human White. Ask for a White move anyway, as a hint or fallback does.
        ChessMove move = assertDoesNotThrow(() -> bestMove(WHITE_MATES_IN_ONE, 2));
        assertEquals(GameStatus.CHECKMATE, statusAfter(WHITE_MATES_IN_ONE, move),
                "engine played " + move.toUci());
    }
}
