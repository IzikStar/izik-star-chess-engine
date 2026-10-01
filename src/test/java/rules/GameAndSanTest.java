package rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3: {@link Game#play} reports a {@link MoveResult}, supports undo, and SAN is headless. */
class GameAndSanTest {

    private static String san(String fen, String uci) {
        return San.of(fen, ChessMove.fromUci(uci));
    }

    @Test
    @DisplayName("SAN for pawn pushes, piece moves, captures, castling, promotion")
    void sanBasics() {
        assertEquals("e4", san(Position.START_FEN, "e2e4"));
        assertEquals("Nf3", san(Position.START_FEN, "g1f3"));
        assertEquals("O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"));
        assertEquals("O-O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1c1"));
        assertEquals("exf6", san("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3", "e5f6"));
        assertEquals("a8=Q", san("8/P6k/8/8/8/8/8/7K w - - 0 1", "a7a8q"));
        assertEquals("a8=N", san("8/P6k/8/8/8/8/8/7K w - - 0 1", "a7a8n"));
    }

    @Test
    @DisplayName("SAN check, mate and disambiguation")
    void sanSuffixAndDisambiguation() {
        assertEquals("Ra8+", san("6k1/5pp1/7p/8/8/8/8/R3K3 w Q - 0 1", "a1a8"));
        assertEquals("Ra8#", san("6k1/5ppp/8/8/8/8/8/R3K3 w Q - 0 1", "a1a8"));
        // two knights can reach d2: b1 and f3 -> file disambiguation
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"));
        // two rooks on the a-file can reach a4 -> rank disambiguation
        assertEquals("R1a4", san("4k3/8/R7/8/8/8/8/R3K3 w - - 0 1", "a1a4"));
        assertEquals("Qxf7#", san("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", "h5f7"));
    }

    @Test
    @DisplayName("play() reports what happened; undo() restores the position")
    void playAndUndo() {
        Game g = new Game("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3");
        MoveResult ep = g.play("e5f6");
        assertTrue(ep.enPassant());
        assertEquals('p', ep.captured());
        assertEquals('P', ep.piece());
        assertEquals(3, ep.moveNumber());
        assertTrue(ep.whiteMoved());
        assertEquals(1, g.plyCount());
        assertTrue(g.undo());
        assertEquals("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3", g.fen());
        assertFalse(g.undo());
    }

    @Test
    @DisplayName("A promotion without a piece becomes a queen; castling is flagged")
    void promotionDefaultAndCastling() {
        MoveResult promo = new Game("8/P6k/8/8/8/8/8/7K w - - 0 1").play("a7a8");
        assertEquals('q', promo.move().promotion());
        MoveResult castle = new Game("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").play("e1g1");
        assertTrue(castle.castling());
    }

    @Test
    @DisplayName("The status after the move includes threefold repetition")
    void statusIncludesThreefold() {
        Game g = new Game();
        String[] shuffle = {"g1f3", "g8f6", "f3g1", "f6g8"};
        MoveResult last = null;
        for (int i = 0; i < 2; i++) {
            for (String m : shuffle) {
                last = g.play(m);
            }
        }
        assertEquals(GameStatus.DRAW_THREEFOLD, last.status());
    }

    @Test
    @DisplayName("Illegal moves are rejected")
    void illegalRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Game().play("e2e5"));
    }
}
