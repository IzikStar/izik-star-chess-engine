package game;

import engine.Engine;
import engine.EngineSelector;
import engine.MinimaxEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.MoveResult;
import rules.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Turn-taking, take-back and events of the headless {@link GameSession} (Phase 3). */
class GameSessionTest {

    /** Runs submitted tasks immediately on the calling thread. */
    static final class DirectExecutor extends AbstractExecutorService {
        @Override public void execute(Runnable r) { r.run(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }

    static final Engine NO_STOCKFISH = new Engine() {
        @Override public ChessMove bestMove(String fen, int level) { return null; }
        @Override public boolean isAvailable() { return false; }
    };

    static final class Recorder implements GameListener {
        final List<MoveResult> moves = new ArrayList<>();
        final List<Boolean> byEngine = new ArrayList<>();
        int resets;
        MoveResult over;
        ChessMove hint;
        @Override public void moveMade(MoveResult m, boolean engine) { moves.add(m); byEngine.add(engine); }
        @Override public void gameOver(MoveResult last) { over = last; }
        @Override public void positionReset() { resets++; }
        @Override public void hint(ChessMove move) { hint = move; }
    }

    private static GameSession direct(GameConfig config, Recorder rec) {
        DirectExecutor direct = new DirectExecutor();
        GameSession s = new GameSession(config,
                new EngineSelector(new MinimaxEngine(new Random(7)), NO_STOCKFISH), direct, direct);
        s.setRandomMoveDelayMs(0);
        s.addListener(rec);
        return s;
    }

    @Test
    @DisplayName("Human vs engine: the engine answers a human move")
    void engineAnswers() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withSkillLevel(2), rec);
        assertNotNull(s.playHumanMove(ChessMove.fromUci("e2e4")));
        assertEquals(2, rec.moves.size());
        assertEquals(List.of(false, true), rec.byEngine);
        assertTrue(s.whiteToMove());
        assertTrue(s.isHumanTurn());
    }

    @Test
    @DisplayName("A human cannot move on the engine's turn or play an illegal move")
    void rejectsOutOfTurnAndIllegal() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withHumanPlaysWhite(false), rec);
        // human plays Black; nothing started yet, so it is White (the engine) to move
        assertNull(s.playHumanMove(ChessMove.fromUci("e2e4")));
        s.start(); // engine (White) moves
        assertEquals(1, rec.moves.size());
        assertNull(s.playHumanMove(ChessMove.fromUci("e7e2")));
    }

    @Test
    @DisplayName("Human vs human: no engine moves")
    void humanVsHuman() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withMode(GameConfig.Mode.HUMAN_VS_HUMAN), rec);
        s.playHumanMove(ChessMove.fromUci("e2e4"));
        assertFalse(s.whiteToMove());
        assertTrue(s.isHumanTurn());
        assertEquals(1, rec.moves.size());
    }

    @Test
    @DisplayName("Take-back in human vs engine undoes the engine's reply and the human's move")
    void undoTwoPlies() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withSkillLevel(2), rec);
        s.playHumanMove(ChessMove.fromUci("e2e4"));
        assertTrue(s.undo());
        assertEquals(Position.START_FEN, s.fen());
        assertEquals(1, rec.resets);
        assertFalse(s.undo());
    }

    @Test
    @DisplayName("Switching the human to Black makes the engine move for White")
    void configChangeStartsEngine() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withSkillLevel(2), rec);
        s.updateConfig(s.config().withHumanPlaysWhite(false));
        assertEquals(1, rec.moves.size());
        assertTrue(rec.moves.get(0).whiteMoved());
    }

    @Test
    @DisplayName("Mate ends the game, fires gameOver, and stops the engine")
    void mateEndsGame() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withMode(GameConfig.Mode.HUMAN_VS_HUMAN), rec);
        for (String m : new String[]{"f2f3", "e7e5", "g2g4", "d8h4"}) {
            s.playHumanMove(ChessMove.fromUci(m));
        }
        assertNotNull(rec.over);
        assertEquals("Qh4#", rec.over.san());
        assertTrue(s.isOver());
        assertTrue(s.legalMoves().isEmpty());
        assertNull(s.playHumanMove(ChessMove.fromUci("e2e4")));
    }

    @Test
    @DisplayName("Engine vs engine plays on its own, on real threads, until stopped")
    void engineVsEngineOnThreads() throws Exception {
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        Recorder rec = new Recorder();
        CountDownLatch tenMoves = new CountDownLatch(10);
        GameSession s = new GameSession(new GameConfig(GameConfig.Mode.ENGINE_VS_ENGINE, true, 2),
                new EngineSelector(new MinimaxEngine(new Random(3)), NO_STOCKFISH), dispatcher);
        s.addListener(rec);
        s.addListener(new GameListener() {
            @Override public void moveMade(MoveResult m, boolean byEngine) { tenMoves.countDown(); }
        });
        dispatcher.execute(s::start);
        assertTrue(tenMoves.await(60, TimeUnit.SECONDS), "engine vs engine stalled");
        dispatcher.submit(s::shutdown).get();
        dispatcher.shutdown();
        assertTrue(rec.byEngine.stream().allMatch(b -> b));
    }

    @Test
    @DisplayName("A hint arrives for the side to move")
    void hint() {
        Recorder rec = new Recorder();
        GameSession s = direct(GameConfig.defaults().withMode(GameConfig.Mode.HUMAN_VS_HUMAN), rec);
        s.requestHint();
        assertNotNull(rec.hint);
        assertTrue(rules.Rules.isLegal(Position.START_FEN, rec.hint));
    }
}
