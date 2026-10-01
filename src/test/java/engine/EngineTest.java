package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.GameStatus;
import rules.Position;
import rules.Rules;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The headless engine boundary (Phase 3 increment 3): FEN in, a legal {@link ChessMove} out. */
class EngineTest {

    private static final String WHITE_MATES_IN_ONE = "7k/1R6/6K1/8/8/8/8/R7 w - - 0 1";
    private static final String BLACK_MATES_IN_ONE = "r7/8/8/8/8/6k1/1r6/7K b - - 0 1";
    private static final String PROMOTION = "8/P6k/8/8/8/8/8/7K w - - 0 1";

    private final MinimaxEngine minimax = new MinimaxEngine(new Random(1));

    @Test
    @DisplayName("Built-in engine mates in one for either colour")
    void matesInOne() {
        for (String fen : new String[]{WHITE_MATES_IN_ONE, BLACK_MATES_IN_ONE}) {
            ChessMove move = minimax.bestMove(fen, 4); // level 4 -> depth 2 + 2 (few pieces)
            assertNotNull(move, fen);
            assertEquals(GameStatus.CHECKMATE, Rules.status(Rules.applyMove(fen, move)),
                    fen + ": engine played " + move.toUci());
        }
    }

    @Test
    @DisplayName("Level 0 plays a random legal move")
    void levelZeroIsLegal() {
        for (int i = 0; i < 20; i++) {
            ChessMove move = minimax.bestMove(Position.START_FEN, 0);
            assertTrue(Rules.isLegal(Position.START_FEN, move), move.toUci());
        }
    }

    @Test
    @DisplayName("A promotion chosen by the search comes back as a promotion move")
    void promotionCarriesThePiece() {
        ChessMove move = minimax.bestMove(PROMOTION, 2);
        assertEquals("a7a8", move.toUci().substring(0, 4));
        assertTrue(move.isPromotion(), "promotion piece missing: " + move.toUci());
    }

    @Test
    @DisplayName("No legal move -> null, not an exception")
    void noMoveIsNull() {
        String mated = "R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1";
        assertNull(minimax.bestMove(mated, 4));
    }

    @Test
    @DisplayName("Search depth follows the old myEngine formula")
    void depthFormula() {
        assertEquals(3, MinimaxEngine.searchDepth(Position.fromFen(Position.START_FEN), 6));
        assertEquals(5, MinimaxEngine.searchDepth(Position.fromFen(PROMOTION), 6));
    }

    @Test
    @DisplayName("Stockfish levels fall back to the built-in engine when Stockfish is unavailable")
    void fallsBackWhenStockfishMissing() {
        Engine missing = new Engine() {
            @Override
            public ChessMove bestMove(String fen, int skillLevel) {
                throw new AssertionError("must not be asked when unavailable");
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        EngineSelector selector = new EngineSelector(minimax, missing);
        assertEquals(GameStatus.CHECKMATE,
                Rules.status(Rules.applyMove(WHITE_MATES_IN_ONE, selector.move(WHITE_MATES_IN_ONE, 16))));
        assertTrue(Rules.isLegal(Position.START_FEN, selector.hint(Position.START_FEN)));
    }

    @Test
    @DisplayName("Stockfish at a missing path reports unavailable and returns no move")
    void stockfishMissingExecutable() {
        StockfishEngine stockfish = new StockfishEngine();
        stockfish.startEngine("/nonexistent/stockfish");
        assertTrue(!stockfish.isAvailable());
        assertNull(stockfish.bestMove(Position.START_FEN, 16));
    }
}
