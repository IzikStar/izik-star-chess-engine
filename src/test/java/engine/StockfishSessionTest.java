package engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Stockfish session (Phase 4, docs/phase-4-research.md Fork C1), driven against
 * {@link FakeUci} in its own process so the tests run without Stockfish installed; one test
 * also uses a real Stockfish when one is found.
 */
class StockfishSessionTest {

    @TempDir
    Path dir;
    private StockfishEngine engine;

    @AfterEach
    void close() {
        if (engine != null) {
            engine.close();
        }
    }

    private StockfishEngine fake(String mode, Path log) {
        String java = ProcessHandle.current().info().command().orElse("java");
        engine = new StockfishEngine(List.of(java, "-cp", System.getProperty("java.class.path"),
                "engine.FakeUci", mode, log.toString()));
        return engine;
    }

    private static List<String> lines(Path log) throws Exception {
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    private static long count(List<String> lines, String exact) {
        return lines.stream().filter(exact::equals).count();
    }

    private static SearchRequest request(List<String> uciMoves, int level, Cancellation cancel) {
        List<ChessMove> moves = new ArrayList<>();
        String fen = Position.START_FEN;
        for (String uci : uciMoves) {
            ChessMove m = ChessMove.fromUci(uci);
            moves.add(m);
            fen = Rules.applyMove(fen, m);
        }
        return new SearchRequest(fen, Position.START_FEN, moves, level, cancel);
    }

    @Test
    @DisplayName("One handshake for a whole game; each move sends the game's moves")
    void oneSessionPerGame() throws Exception {
        Path log = dir.resolve("log");
        StockfishEngine sf = fake("normal", log);
        assertNotNull(sf.bestMove(request(List.of(), 9, Cancellation.NONE)));
        assertNotNull(sf.bestMove(request(List.of("e2e4", "e7e5"), 9, Cancellation.NONE)));
        assertNotNull(sf.bestMove(request(List.of("e2e4", "e7e5", "g1f3", "b8c6"), 13, Cancellation.NONE)));
        List<String> sent = lines(log);
        assertEquals(1, count(sent, "uci"), sent.toString());
        assertEquals(1, count(sent, "ucinewgame"), sent.toString());
        assertTrue(sent.contains("position fen " + Position.START_FEN + " moves e2e4 e7e5 g1f3 b8c6"), sent.toString());
        assertTrue(sent.contains("setoption name UCI_LimitStrength value true"), sent.toString());
        assertTrue(sent.contains("setoption name UCI_Elo value 2150"), sent.toString());
        assertTrue(sent.contains("setoption name UCI_LimitStrength value false"), sent.toString());
        assertEquals(1, count(sent, "setoption name UCI_Elo value 2150"), "the strength is only resent when it changes");
        assertTrue(sent.contains("go movetime 500"), sent.toString());
        assertTrue(sent.contains("go movetime 1000"), sent.toString());
        assertEquals(500, StockfishEngine.moveTimeMs(12), "Level 12");
        assertEquals(1000, StockfishEngine.moveTimeMs(13), "Level 13, full strength");
        assertEquals(4000, StockfishEngine.moveTimeMs(EngineSelector.HINT_LEVEL), "hints");
    }

    @Test
    @DisplayName("With a clock, Stockfish thinks no longer than the move's budget")
    void keepsToTheBudget() throws Exception {
        Path log = dir.resolve("log");
        StockfishEngine sf = fake("normal", log);
        assertNotNull(sf.bestMove(request(List.of(), 13, Cancellation.NONE).withTimeBudgetMs(120)));
        assertNotNull(sf.bestMove(request(List.of("e2e4", "e7e5"), 9, Cancellation.NONE).withTimeBudgetMs(5_000)));
        List<String> sent = lines(log);
        assertTrue(sent.contains("go movetime 120"), sent.toString());
        assertTrue(sent.contains("go movetime 500"), "a roomy budget leaves the level's own time: " + sent);
    }

    @Test
    @DisplayName("A shorter move list (take-back) or another start position starts a new game")
    void newGameOnTakeBack() throws Exception {
        Path log = dir.resolve("log");
        StockfishEngine sf = fake("normal", log);
        sf.bestMove(request(List.of("e2e4", "e7e5"), 13, Cancellation.NONE));
        sf.bestMove(request(List.of(), 13, Cancellation.NONE));
        assertEquals(2, count(lines(log), "ucinewgame"));
    }

    @Test
    @DisplayName("Cancelling sends stop, returns at once, and keeps the process")
    void cancelSendsStop() throws Exception {
        Path log = dir.resolve("log");
        StockfishEngine sf = fake("hang", log);
        assertNull(sf.bestMove(request(List.of(), 13, alreadyCancelledLater(sf))));
        Cancellation cancel = new Cancellation();
        CompletableFuture<ChessMove> pending = CompletableFuture.supplyAsync(
                () -> sf.bestMove(request(List.of("e2e4"), 13, cancel)));
        Thread.sleep(300);
        long start = System.nanoTime();
        cancel.cancel();
        assertNull(pending.get(5, TimeUnit.SECONDS));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000, "returns soon after cancel");
        List<String> sent = lines(log);
        assertTrue(sent.contains("stop"), sent.toString());
        assertEquals(1, count(sent, "uci"), "the same process serves on");
        assertTrue(sf.isAvailable());
    }

    /** A cancellation that fires shortly after the search starts (the warm-up request). */
    private static Cancellation alreadyCancelledLater(StockfishEngine sf) {
        Cancellation c = new Cancellation();
        CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(c::cancel);
        return c;
    }

    @Test
    @DisplayName("A crashing engine is restarted, then given up on after repeated failures")
    void crashThenUnavailable() throws Exception {
        Path log = dir.resolve("log");
        StockfishEngine sf = fake("crash", log);
        for (int i = 0; i < StockfishEngine.MAX_FAILURES; i++) {
            assertTrue(sf.isAvailable());
            assertNull(sf.bestMove(request(List.of(), 13, Cancellation.NONE)));
        }
        assertFalse(sf.isAvailable());
        assertEquals(StockfishEngine.MAX_FAILURES, count(lines(log), "uci"), "one restart per failure");
    }

    @Test
    @DisplayName("An illegal answer is rejected")
    void illegalAnswerRejected() {
        StockfishEngine sf = fake("illegal", dir.resolve("log"));
        assertNull(sf.bestMove(request(List.of(), 13, Cancellation.NONE)));
    }

    @Test
    @DisplayName("A missing executable makes the engine unavailable and the selector falls back")
    void missingExecutable() {
        engine = new StockfishEngine(List.of("/nonexistent/stockfish"));
        assertNull(engine.bestMove(request(List.of(), 13, Cancellation.NONE)));
        assertFalse(engine.isAvailable());
        EngineSelector selector = new EngineSelector(new MinimaxEngine(), engine);
        assertTrue(Rules.isLegal(Position.START_FEN, selector.move(Position.START_FEN, 16)));
    }

    @Test
    @DisplayName("Real Stockfish (when installed): legal moves through a game and mate in one")
    void realStockfish() {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        engine = new StockfishEngine(List.of(path));
        List<String> played = new ArrayList<>();
        for (int ply = 0; ply < 6; ply++) {
            SearchRequest r = request(played, 13, Cancellation.NONE);
            ChessMove move = engine.bestMove(r);
            assertNotNull(move);
            assertTrue(Rules.isLegal(r.fen(), move));
            played.add(move.toUci());
        }
        // Full strength: below Skill Level 20 Stockfish picks weaker moves at random, mates included.
        String mateInOne = "7k/1R6/6K1/8/8/8/8/R7 w - - 0 1";
        ChessMove mate = engine.bestMove(SearchRequest.of(mateInOne, EngineSelector.HINT_LEVEL));
        assertEquals(rules.GameStatus.CHECKMATE, Rules.status(Rules.applyMove(mateInOne, mate)));
    }
}
