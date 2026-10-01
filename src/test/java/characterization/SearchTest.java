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
    @DisplayName("Engine as White returns a legal move from the opening")
    void whiteEngineLegalMoveWhenHumanIsBlack() {
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
        ChessMove move = bestMove(WHITE_MATES_IN_ONE, 2);
        assertEquals(GameStatus.CHECKMATE, statusAfter(WHITE_MATES_IN_ONE, move),
                "engine played " + move.toUci());
    }

    /**
     * Was Bug A ("the engine stops playing"): the search decided whether it was at its root by
     * reading UI flags, and threw a NullPointerException when they disagreed with the side to
     * move. It now always searches for the side to move; Phase 3 deleted those flags entirely.
     */
    @Test
    @DisplayName("Search for the side to move does not depend on the UI's idea of who the human is")
    void searchIgnoresUiFlags() {
        ChessMove move = assertDoesNotThrow(() -> bestMove(WHITE_MATES_IN_ONE, 2));
        assertEquals(GameStatus.CHECKMATE, statusAfter(WHITE_MATES_IN_ONE, move),
                "engine played " + move.toUci());
    }

    /**
     * The search's repetition check hashes positions. Pawns used to hash as empty squares (and
     * the side to move was ignored), so two pawn pushes looked like a threefold repetition.
     */
    @Test
    @DisplayName("Search position hash sees pawns and the side to move")
    void searchHashSeesPawnsAndSideToMove() {
        long start = ai.BitBoard.ZobristHashing.computeHash(bit(START));
        long afterE4Placement = ai.BitBoard.ZobristHashing.computeHash(
                bit("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1"));
        long blackToMove = ai.BitBoard.ZobristHashing.computeHash(
                bit("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1"));
        assertTrue(start != afterE4Placement, "a pawn move changes the hash");
        assertTrue(start != blackToMove, "the side to move changes the hash");
    }
}
