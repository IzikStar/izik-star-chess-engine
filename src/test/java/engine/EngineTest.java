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
    @DisplayName("Search depth: level - 1, deeper with few pieces left, at most depth 7")
    void depthFormula() {
        assertEquals(3, MinimaxEngine.searchDepth(Position.fromFen(Position.START_FEN), 4));
        assertEquals(5, MinimaxEngine.searchDepth(Position.fromFen(PROMOTION), 4));
        assertEquals(1, MinimaxEngine.searchDepth(Position.fromFen(Position.START_FEN), 1));
        assertEquals(7, MinimaxEngine.searchDepth(Position.fromFen(Position.START_FEN), Levels.TOP_BUILT_IN));
        assertEquals(7, MinimaxEngine.searchDepth(Position.fromFen(Position.START_FEN), Levels.MAX));
    }

    @Test
    @DisplayName("Stockfish levels fall back to the built-in engine when Stockfish is unavailable")
    void fallsBackWhenStockfishMissing() {
        Engine missing = new Engine() {
            @Override
            public ChessMove bestMove(SearchRequest request) {
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
    @DisplayName("Without Stockfish, Levels 8-10 get the built-in engine at its strongest level, not Level 2")
    void fallbackIsTheStrongestBuiltInLevel() {
        Engine missing = new Engine() {
            @Override
            public ChessMove bestMove(SearchRequest request) {
                throw new AssertionError("must not be asked when unavailable");
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        java.util.List<Integer> levels = new java.util.ArrayList<>();
        Engine builtIn = request -> {
            levels.add(request.skillLevel());
            return minimax.bestMove(request.withSkillLevel(1));
        };
        EngineSelector selector = new EngineSelector(builtIn, missing);
        selector.move(Position.START_FEN, Levels.STOCKFISH_FROM);
        selector.move(Position.START_FEN, Levels.MAX);
        assertEquals(java.util.List.of(Levels.TOP_BUILT_IN, Levels.TOP_BUILT_IN), levels);
        assertTrue(!selector.stockfishAvailable());
    }

    @Test
    @DisplayName("The built-in engine stops at its time cap and still plays a legal move")
    void builtInRespectsTimeCap() {
        MinimaxEngine capped = new MinimaxEngine(new java.util.Random(1), 300);
        long start = System.nanoTime();
        ChessMove move = capped.bestMove(Position.START_FEN, 12); // depth 6: minutes uncapped
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(Rules.isLegal(Position.START_FEN, move));
        assertTrue(ms < 2000, "took " + ms + " ms");
    }

    @Test
    @DisplayName("A cancelled request gets no move")
    void cancelledRequestGetsNoMove() {
        Cancellation cancel = new Cancellation();
        cancel.cancel();
        SearchRequest request = new SearchRequest(Position.START_FEN, Position.START_FEN,
                java.util.List.of(), 6, cancel);
        assertNull(new MinimaxEngine().bestMove(request));
    }

    @Test
    @DisplayName("Iterative deepening without a stop plays the same move as one fixed-depth search")
    void deepeningMatchesFixedDepth() {
        String fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5";
        for (int depth = 1; depth <= 3; depth++) {
            int fixed = ai.Minimax.getBestMove(ai.board.Boards.fromFen(fen), depth);
            int deepened = ai.Minimax.getBestMove(ai.board.Boards.fromFen(fen), depth, () -> false);
            assertEquals(fixed, deepened, "depth " + depth);
        }
    }
}
