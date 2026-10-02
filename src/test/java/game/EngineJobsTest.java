package game;

import engine.EngineSelector;
import engine.MinimaxEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.MoveResult;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine jobs on real threads: cancelling a search must free the engine thread, and hint
 * requests must not pile up in front of the engine's move (docs/phase-4-research.md §3.1, §3.2).
 */
class EngineJobsTest {

    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor();
    private GameSession session;

    @AfterEach
    void tearDown() {
        if (session != null) {
            dispatcher.submit(session::shutdown);
        }
        dispatcher.shutdown();
    }

    private GameSession start(int level, CountDownLatch engineMoved) throws Exception {
        session = dispatcher.submit(() -> {
            GameSession s = new GameSession(GameConfig.defaults().withSkillLevel(level),
                    new EngineSelector(new MinimaxEngine(), GameSessionTest.NO_STOCKFISH), dispatcher);
            s.addListener(new GameListener() {
                @Override public void moveMade(MoveResult move, boolean byEngine) {
                    if (byEngine) {
                        engineMoved.countDown();
                    }
                }
            });
            return s;
        }).get();
        return session;
    }

    private void onDispatcher(Runnable action) throws Exception {
        dispatcher.submit(action).get();
    }

    @Test
    @Tag("known-bug")
    @DisplayName("A take-back during a long search frees the engine for the next move")
    void undoCancelsTheRunningSearch() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            CountDownLatch replied = new CountDownLatch(1);
            GameSession s = start(12, replied); // depth 6: minutes without a cap
            onDispatcher(() -> s.playHumanMove(ChessMove.fromUci("e2e4")));
            Thread.sleep(300);
            onDispatcher(() -> {
                s.undo();
                s.playHumanMove(ChessMove.fromUci("d2d4"));
            });
            assertTrue(replied.await(10, TimeUnit.SECONDS),
                    "the engine should answer 1.d4 within its time cap, not after the stale search");
        });
    }

    @Test
    @Tag("known-bug")
    @DisplayName("Repeated hint requests do not delay the engine's move")
    void hintsDoNotStarveTheEngine() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            CountDownLatch replied = new CountDownLatch(1);
            GameSession s = start(2, replied); // the engine's own move is depth 1, instant
            for (int i = 0; i < 5; i++) {
                onDispatcher(s::requestHint); // each hint is a depth-5 search here (no Stockfish)
            }
            onDispatcher(() -> s.playHumanMove(ChessMove.fromUci("e2e4")));
            assertTrue(replied.await(10, TimeUnit.SECONDS),
                    "the engine's move should not wait behind queued hints");
        });
    }
}
