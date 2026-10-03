package game;

import engine.EngineSelector;
import engine.MinimaxEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clocks, resigning and draw offers: the endings off the board. */
class GameEndingsTest {

    private static final GameConfig FRIENDS = GameConfig.defaults().withMode(GameConfig.Mode.HUMAN_VS_HUMAN);

    /** A fake time source, in nanoseconds, moved by hand. */
    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    private void advanceMs(long ms) {
        now.addAndGet(ms * 1_000_000);
    }

    private static final class Ends implements GameListener {
        final List<GameEnd> ended = new ArrayList<>();
        final List<String> draws = new ArrayList<>();
        final CountDownLatch endedLatch = new CountDownLatch(1);
        int engineMoves;
        @Override public void moveMade(rules.MoveResult m, boolean byEngine) { if (byEngine) engineMoves++; }
        @Override public void gameEnded(GameEnd end) { ended.add(end); endedLatch.countDown(); }
        @Override public void drawOffered(boolean byWhite) { draws.add("offer " + (byWhite ? "white" : "black")); }
        @Override public void drawDeclined(boolean byWhite) { draws.add("declined " + (byWhite ? "white" : "black")); }
    }

    private GameSession session(GameConfig config, Ends ends) {
        GameSessionTest.DirectExecutor direct = new GameSessionTest.DirectExecutor();
        GameSession s = new GameSession(config,
                new EngineSelector(new MinimaxEngine(new Random(7)), GameSessionTest.NO_STOCKFISH), direct, direct);
        s.setRandomMoveDelayMs(0);
        s.setNanoTime(now::get);
        s.addListener(ends);
        return s;
    }

    private static void play(GameSession s, String... ucis) {
        for (String uci : ucis) {
            assertNotNull(s.playHumanMove(ChessMove.fromUci(uci)), uci);
        }
    }

    // ---- the clock ---------------------------------------------------------

    @Test
    @DisplayName("Clock: nothing runs before the first move; then the side to move's time runs, with increment")
    void clockCounts() {
        Clock clock = new Clock(new TimeControl(60_000, 2_000), now::get);
        advanceMs(5_000);
        assertNull(clock.running());
        assertEquals(60_000, clock.remainingMs(true));

        clock.moveMade(true); // White's first move: no increment, Black's time starts
        assertEquals(60_000, clock.remainingMs(true));
        advanceMs(7_000);
        assertEquals(53_000, clock.remainingMs(false));
        clock.moveMade(false); // Black moved after 7 s and gets 2 s back
        assertEquals(55_000, clock.remainingMs(false));
        assertEquals(Boolean.TRUE, clock.running());

        advanceMs(60_000);
        assertEquals(0, clock.remainingMs(true));
        assertTrue(clock.flagged());
        clock.stop();
        assertNull(clock.running());
        assertEquals(55_000, clock.remainingMs(false));
    }

    @Test
    @DisplayName("Time control tags: 5+3 is 300+3, an untimed game is -")
    void timeControlTag() {
        assertEquals("300+3", TimeControl.ofMinutes(5, 3).pgnTag());
        assertEquals("60", TimeControl.ofMinutes(1, 0).pgnTag());
        assertEquals("-", TimeControl.NONE.pgnTag());
        assertFalse(TimeControl.NONE.isTimed());
    }

    @Test
    @DisplayName("A move made after the flag fell loses on time instead")
    void moveAfterFlagLoses() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        s.newGame(Position.START_FEN, TimeControl.ofMinutes(1, 0));
        play(s, "e2e4");
        advanceMs(61_000);
        assertNull(s.playHumanMove(ChessMove.fromUci("e7e5")));
        assertEquals(List.of(GameEnd.flagged(false, true)), ends.ended);
        assertEquals("1-0", s.result());
        assertTrue(s.isOver());
        assertFalse(s.isHumanTurn());
        assertFalse(s.undo(), "a loss on time cannot be taken back");
    }

    @Test
    @DisplayName("Running out of time against a lone king and knight is a draw")
    void timeoutAgainstInsufficientMaterial() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        s.newGame("4k3/8/8/8/8/8/4P3/4K1N1 w - - 0 1", TimeControl.ofMinutes(1, 0));
        play(s, "e1d1", "e8d8");
        advanceMs(61_000);
        assertNull(s.playHumanMove(ChessMove.fromUci("d1c1"))); // White flagged; Black has a bare king
        assertEquals(GameEnd.Reason.TIMEOUT_VS_INSUFFICIENT_MATERIAL, ends.ended.get(0).reason());
        assertEquals("1/2-1/2", s.result());

        assertTrue(GameSession.canMate(Position.fromFen("4k3/8/8/8/8/8/4P3/4K1N1 w - - 0 1"), true));
        assertFalse(GameSession.canMate(Position.fromFen("4k3/8/8/8/8/8/8/4K1N1 w - - 0 1"), true));
        assertTrue(GameSession.canMate(Position.fromFen("4k3/8/8/8/8/8/8/2B1K1N1 w - - 0 1"), true));
    }

    @Test
    @DisplayName("The flag falls by itself: the clock thread ends the game without anyone moving")
    void flagFallsOnItsOwn() throws InterruptedException {
        Ends ends = new Ends();
        GameSessionTest.DirectExecutor direct = new GameSessionTest.DirectExecutor();
        GameSession s = new GameSession(FRIENDS,
                new EngineSelector(new MinimaxEngine(new Random(7)), GameSessionTest.NO_STOCKFISH), direct, direct);
        s.addListener(ends);
        s.newGame(Position.START_FEN, new TimeControl(150, 0));
        play(s, "e2e4");
        assertTrue(ends.endedLatch.await(5, TimeUnit.SECONDS), "the flag should fall within the 150 ms");
        assertEquals(GameEnd.Reason.TIMEOUT, ends.ended.get(0).reason());
        assertFalse(ends.ended.get(0).white(), "Black was to move");
        s.shutdown();
    }

    @Test
    @DisplayName("A take-back keeps the times; the side to move's clock runs again")
    void undoKeepsTimes() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        s.newGame(Position.START_FEN, TimeControl.ofMinutes(5, 0));
        play(s, "e2e4");
        advanceMs(10_000);
        play(s, "e7e5");
        advanceMs(3_000);
        assertTrue(s.undo());
        assertEquals(Boolean.FALSE, s.clock().running());
        assertEquals(297_000, s.clock().remainingMs(true), "White's first move was not timed");
        assertEquals(290_000, s.clock().remainingMs(false));
        assertTrue(s.undo());
        assertNull(s.clock().running(), "back at the start, no clock runs");
    }

    @Test
    @DisplayName("A new game resets the clock; an untimed game has none")
    void newGameResetsClock() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        s.newGame(Position.START_FEN, TimeControl.ofMinutes(3, 2));
        play(s, "e2e4");
        advanceMs(20_000);
        s.newGame();
        assertEquals(180_000, s.clock().remainingMs(false), "newGame() keeps the time control");
        s.newGame(Position.START_FEN, TimeControl.NONE);
        assertNull(s.clock());
    }

    // ---- resigning ---------------------------------------------------------

    @Test
    @DisplayName("Resigning against the engine: the human's side loses and the engine stops")
    void resignAgainstEngine() {
        Ends ends = new Ends();
        GameSession s = session(GameConfig.defaults().withHumanPlaysWhite(false).withSkillLevel(2), ends);
        s.start();
        assertEquals(1, ends.engineMoves);
        assertTrue(s.resign());
        assertEquals(List.of(GameEnd.resigned(false)), ends.ended);
        assertEquals("1-0", s.result());
        assertFalse(s.resign(), "only once");
        assertFalse(s.canOfferDraw());
        assertTrue(s.legalMoves().isEmpty());
    }

    @Test
    @DisplayName("Between two humans the side to move resigns; the engine watching itself cannot")
    void resignSides() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        play(s, "e2e4");
        assertTrue(s.resign());
        assertEquals("1-0", s.result());

        GameSession watch = session(new GameConfig(GameConfig.Mode.ENGINE_VS_ENGINE, true, 0), new Ends());
        assertFalse(watch.canResign());
        assertFalse(watch.resign());
    }

    // ---- draw offers -------------------------------------------------------

    @Test
    @DisplayName("The engine accepts a draw in a level position")
    void engineAcceptsLevelDraw() {
        Ends ends = new Ends();
        GameSession s = session(GameConfig.defaults().withSkillLevel(2), ends);
        assertTrue(s.offerDraw());
        assertEquals(List.of(GameEnd.agreed()), ends.ended);
        assertEquals("1/2-1/2", s.result());
    }

    @Test
    @DisplayName("The engine declines when it is better, and the human must move before offering again")
    void engineDeclinesWhenBetter() {
        Ends ends = new Ends();
        // the human (White) has no queen
        GameSession s = session(FRIENDS, ends);
        s.newGame("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", TimeControl.NONE);
        s.updateConfig(GameConfig.defaults().withSkillLevel(2));
        assertTrue(s.canOfferDraw());
        assertTrue(s.offerDraw());
        assertEquals(List.of("declined white"), ends.draws);
        assertFalse(s.isOver());
        assertFalse(s.canOfferDraw());
        assertFalse(s.offerDraw());
        play(s, "g1f3"); // the engine replies, and the human may offer again
        assertTrue(s.canOfferDraw());
    }

    @Test
    @DisplayName("The engine accepts in a repeated position even when it is better")
    void engineAcceptsRepetition() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        s.newGame("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", TimeControl.NONE);
        play(s, "g1f3", "g8f6", "f3g1", "f6g8");
        s.updateConfig(GameConfig.defaults().withSkillLevel(2));
        assertTrue(s.offerDraw());
        assertEquals(List.of(GameEnd.agreed()), ends.ended);
    }

    @Test
    @DisplayName("Draw offers engine-side scores: a queen up is far above the threshold, the start is level")
    void engineScoreSign() {
        assertTrue(engine.DrawOffers.engineAccepts(Position.START_FEN, false));
        String noBlackQueen = "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        assertFalse(engine.DrawOffers.engineAccepts(noBlackQueen, true), "White is a queen up");
        assertTrue(engine.DrawOffers.engineAccepts(noBlackQueen, false), "Black is a queen down");
    }

    @Test
    @DisplayName("Two humans: an offer waits for an answer; a move by the other side declines it")
    void humanDrawOffers() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        assertTrue(s.offerDraw());
        assertEquals(Boolean.TRUE, s.drawOfferBy());
        assertFalse(s.canOfferDraw(), "one offer at a time");
        play(s, "e2e4");
        assertEquals(Boolean.TRUE, s.drawOfferBy(), "the offer stands while Black thinks");
        play(s, "e7e5");
        assertNull(s.drawOfferBy(), "Black moved instead of answering");

        assertTrue(s.offerDraw()); // White again
        assertTrue(s.answerDraw(false));
        assertEquals(List.of("offer white", "offer white", "declined white"), ends.draws);
        assertFalse(s.answerDraw(true), "nothing waiting");

        play(s, "g1f3");
        assertTrue(s.offerDraw()); // Black
        assertTrue(s.answerDraw(true));
        assertEquals(List.of(GameEnd.agreed()), ends.ended);
    }

    // ---- PGN ---------------------------------------------------------------

    @Test
    @DisplayName("Loading a PGN replays its moves; a bad one changes nothing")
    void loadPgn() {
        Ends ends = new Ends();
        GameSession s = session(FRIENDS, ends);
        play(s, "d2d4");
        try {
            s.loadPgn("1. e4 e5 2. Ke3");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Ke3"), expected.getMessage());
        }
        assertEquals(1, s.moves().size(), "the old game is untouched");
        s.loadPgn("[Event \"x\"]\n\n1. e4 e5 2. Nf3 {a comment} Nc6 (2... d6) 3. Bb5 a6 *");
        assertEquals(6, s.moves().size());
        assertEquals("a6", s.moves().get(5).san());
        assertTrue(s.whiteToMove());
        assertNull(s.clock());
    }

    // ---- carrying on a saved game ------------------------------------------

    @Test
    @DisplayName("Resume: the moves are replayed, each side keeps its time, and the side to move's clock runs")
    void resumeKeepsTimes() {
        GameSession s = session(FRIENDS, new Ends());
        s.resume(Position.START_FEN, List.of(ChessMove.fromUci("e2e4"), ChessMove.fromUci("e7e5")),
                new TimeControl(180_000, 2_000), 90_000, 120_000, java.time.LocalDate.of(2026, 10, 1));
        assertEquals(2, s.moves().size());
        assertEquals(Boolean.TRUE, s.clock().running());
        assertEquals(java.time.LocalDate.of(2026, 10, 1), s.date());
        advanceMs(10_000);
        assertEquals(80_000, s.clock().remainingMs(true));
        assertEquals(120_000, s.clock().remainingMs(false));
        play(s, "g1f3");
        assertEquals(82_000, s.clock().remainingMs(true));

        // an illegal saved game changes nothing
        assertThrows(IllegalArgumentException.class, () -> s.resume(Position.START_FEN, List.of(ChessMove.fromUci("e2e5")),
                TimeControl.NONE, 0, 0, java.time.LocalDate.now()));
        assertEquals(3, s.moves().size());
    }
}
