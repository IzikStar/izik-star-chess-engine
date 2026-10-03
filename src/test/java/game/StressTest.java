package game;

import engine.EngineSelector;
import engine.Levels;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.MoveResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unattended play on real threads (Phase 4 exit criterion, docs/phase-4-research.md Fork E1):
 * engine-vs-engine games must all end, and a storm of moves, take-backs, colour flips, new games
 * and hint requests must never leave the engine silent. Run with {@code mvn test -Pstress}; the
 * {@code smoke} methods are short versions that run with {@code -Psmoke}.
 */
class StressTest {

    /** Plays {@code games} engine-vs-engine games and returns how many ended. */
    private static int engineVsEngine(EngineSelector engines, int level, int games, long perGameSeconds)
            throws Exception {
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        BlockingQueue<MoveResult> ends = new LinkedBlockingQueue<>();
        GameSession s = dispatcher.submit(() -> {
            GameSession session = new GameSession(
                    new GameConfig(GameConfig.Mode.ENGINE_VS_ENGINE, true, level), engines, dispatcher);
            session.setRandomMoveDelayMs(0);
            session.addListener(new GameListener() {
                @Override public void gameOver(MoveResult last) { ends.add(last); }
            });
            session.start();
            return session;
        }).get();
        int ended = 0;
        try {
            for (int g = 0; g < games; g++) {
                MoveResult end = ends.poll(perGameSeconds, TimeUnit.SECONDS);
                assertNotNull(end, "game " + g + " did not end in " + perGameSeconds + " s at "
                        + dispatcher.submit(s::fen).get());
                ended++;
                dispatcher.submit(() -> s.newGame()).get();
            }
        } finally {
            dispatcher.submit(s::shutdown).get();
            dispatcher.shutdown();
        }
        return ended;
    }

    /** Random human moves against the engine, mixed with take-backs, flips, new games and hints. */
    private static void churn(int operations, int level) throws Exception {
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        GameSession s = dispatcher.submit(() -> new GameSession(GameConfig.defaults().withSkillLevel(level),
                new EngineSelector(new MinimaxEngine(), GameSessionTest.NO_STOCKFISH), dispatcher)).get();
        Random random = new Random(1);
        AtomicInteger errors = new AtomicInteger();
        try {
            for (int i = 0; i < operations; i++) {
                int op = random.nextInt(10);
                dispatcher.submit(() -> {
                    try {
                        if (op < 6 && s.isHumanTurn()) {
                            List<ChessMove> legal = s.legalMoves();
                            s.playHumanMove(legal.get(random.nextInt(legal.size())));
                        } else if (op == 6) {
                            s.undo();
                        } else if (op == 7) {
                            s.updateConfig(s.config().withHumanPlaysWhite(random.nextBoolean()));
                        } else if (op == 8 && random.nextInt(5) == 0) {
                            s.newGame();
                        } else if (op == 9) {
                            s.requestHint();
                        }
                        if (s.isOver()) {
                            s.newGame();
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                        t.printStackTrace();
                    }
                }).get();
                Thread.sleep(random.nextInt(3));
            }
            assertEquals(0, errors.get());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean answered = false;
            while (!answered && System.nanoTime() < deadline) {
                answered = dispatcher.submit(() -> s.isHumanTurn() || s.isOver()).get();
                Thread.sleep(20);
            }
            assertTrue(answered, "the engine must answer after the storm");
        } finally {
            dispatcher.submit(s::shutdown).get();
            dispatcher.shutdown();
        }
    }

    private static EngineSelector builtInOnly() {
        return new EngineSelector(new MinimaxEngine(), GameSessionTest.NO_STOCKFISH);
    }

    @Test
    @Tag("stress")
    @DisplayName("50 engine-vs-engine games at Level 2 all end")
    void fiftyQuickGames() throws Exception {
        assertEquals(50, engineVsEngine(builtInOnly(), 2, 50, 120));
    }

    @Test
    @Tag("stress")
    @DisplayName("Engine-vs-engine at the app's computer-game level (Level 4) ends, 5 games")
    void computerGameLevel() throws Exception {
        assertEquals(5, engineVsEngine(builtInOnly(), 4, 5, 600));
    }

    @Test
    @Tag("stress")
    @DisplayName("Stockfish vs Stockfish (when installed), 3 games at Level 9")
    void stockfishGames() throws Exception {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        EngineSelector engines = new EngineSelector(new MinimaxEngine(), new StockfishEngine(List.of(path)));
        assertEquals(3, engineVsEngine(engines, Levels.STOCKFISH_FROM, 3, 600));
    }

    @Test
    @Tag("stress")
    @DisplayName("3,000 random operations with take-backs and hints never silence the engine")
    void churnLong() throws Exception {
        churn(3000, 2);
    }

    @Test
    @Tag("smoke")
    @DisplayName("Short stress: 5 quick engine-vs-engine games and 300 random operations")
    void stressSmoke() throws Exception {
        assertEquals(5, engineVsEngine(builtInOnly(), 2, 5, 120));
        churn(300, 2);
    }
}
