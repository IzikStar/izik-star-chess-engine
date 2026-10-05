package engine;

import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Rules;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 R4d: the engines play the request's variant. */
class VariantEngineTest {

    private static SearchRequest request(ai.variant.Variant variant, String fen, int level) {
        return SearchRequest.of(fen, level).withVariant(variant);
    }

    @Test
    @DisplayName("The built-in engine plays antichess's forced capture, and the winning sacrifice")
    void antichess() {
        MinimaxEngine engine = new MinimaxEngine(new Random(1), 5000, ai.eval.ChessEvaluate.DEFAULT, 0);
        String forced = Rules.applyMove(Variants.ANTICHESS,
                Rules.applyMove(Variants.ANTICHESS, Variants.ANTICHESS.startFen(), ChessMove.fromUci("e2e3")),
                ChessMove.fromUci("b7b5"));
        assertEquals("f1b5", engine.bestMove(request(Variants.ANTICHESS, forced, 4)).toUci());
        ChessMove sacrifice = engine.bestMove(request(Variants.ANTICHESS, "1k6/8/8/8/8/8/8/R7 w - - 0 1", 4));
        assertTrue(List.of("a1a8", "a1a7").contains(sacrifice.toUci()), sacrifice.toUci());
    }

    @Test
    @DisplayName("King of the Hill: the engine walks its king to the centre to win")
    void kingOfTheHill() {
        MinimaxEngine engine = new MinimaxEngine(new Random(1), 5000, ai.eval.ChessEvaluate.DEFAULT, 0);
        ChessMove move = engine.bestMove(request(Variants.KING_OF_THE_HILL, "7k/8/8/8/8/3K4/8/8 w - - 0 1", 3));
        assertNotNull(move);
        assertTrue(List.of("d3d4", "d3e4").contains(move.toUci()), move.toUci());
    }

    @Test
    @DisplayName("Three-check: the history of a game reads its half-move clock past the checks field")
    void threeCheckHistory() {
        String fen = "4k3/8/8/8/8/8/8/R3K3 w - - 3+3 4 20";
        long[] keys = MinimaxEngine.gameHistory(Variants.THREE_CHECK, List.of(
                "4k3/8/8/8/8/8/8/R3K3 w - - 3+3 0 18", "4k3/8/8/8/8/8/8/R3K3 b - - 3+3 1 18",
                "4k3/8/8/8/8/8/8/R3K3 w - - 3+3 2 19", "4k3/8/8/8/8/8/8/R3K3 b - - 3+3 3 19"), fen);
        assertEquals(4, keys.length);
    }

    @Test
    @DisplayName("Stockfish plays chess only: in a variant its levels and hints go to the built-in engine")
    void stockfishOnlyForChess() {
        AtomicInteger asked = new AtomicInteger();
        Engine stockfish = new Engine() {
            @Override
            public ChessMove bestMove(SearchRequest request) {
                asked.incrementAndGet();
                return Rules.legalMoves(request.fen()).get(0);
            }
        };
        EngineSelector selector = new EngineSelector(new MinimaxEngine(new Random(1), 200), stockfish);
        String start = Variants.ANTICHESS.startFen();
        assertNotNull(selector.move(request(Variants.ANTICHESS, start, Levels.STOCKFISH_FROM + 1)));
        assertNotNull(selector.hint(request(Variants.ANTICHESS, start, Levels.HINT)));
        assertEquals(0, asked.get());
        selector.move(request(Variants.CHESS, start, Levels.STOCKFISH_FROM + 1));
        assertEquals(1, asked.get());
    }
}
