package characterization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.GameStatus;
import rules.Rules;

import static characterization.SearchTestSupport.BLACK_MATES_IN_ONE;
import static characterization.SearchTestSupport.bestMove;
import static characterization.SearchTestSupport.statusAfter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 safety net for the minimax search (until now untested). Pins what the engine does
 * today under the stock settings (human plays White, engine plays Black). The two cases that
 * are wrong today live in {@link SearchKnownBugsTest}.
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
}
