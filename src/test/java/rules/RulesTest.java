package rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 — characterization of the NEW unified rules authority ({@link Rules} / {@link Game}).
 *
 * <p>These pin the TARGET behaviour the phase converges on: one headless FEN/UCI API that gets
 * move counts, mate/stalemate AND the draw rules right. They pass today because the facade
 * already delegates generation to the (more-correct) bitboard engine. Later increments re-point
 * the OO path and the search at this facade and delete the duplicated logic; the
 * {@code -Pknown-bugs} reds flip to green there, not here.
 *
 * <p>Standalone (not {@code CharacterizationTestBase}): the new module has no Swing globals to
 * reset — that is the point of the phase.
 */
class RulesTest {

    static final String START = Position.START_FEN;
    static final String FOOLS_MATE = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";
    static final String BACK_RANK_MATE = "R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1";
    static final String STALEMATE = "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1";
    static final String CASTLING_OPEN = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
    static final String EN_PASSANT = "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3";
    static final String PROMOTION = "8/P6k/8/8/8/8/8/7K w - - 0 1";

    // ---- move generation ---------------------------------------------------

    @Test
    @DisplayName("20 legal moves from the initial position (OO path only finds 12)")
    void startCount() {
        assertEquals(20, Rules.legalMoves(START).size());
    }

    @Test
    @DisplayName("every generated move is well-formed UCI")
    void movesWellFormed() {
        for (ChessMove m : Rules.legalMoves(CASTLING_OPEN)) {
            assertTrue(m.toUci().matches("[a-h][1-8][a-h][1-8][qrbn]?"), m.toUci());
        }
    }

    @Test
    @DisplayName("castling appears in the legal move list as e1g1 / e1c1")
    void castlingMoves() {
        List<String> ucis = Rules.legalMoves(CASTLING_OPEN).stream().map(ChessMove::toUci).toList();
        assertTrue(ucis.contains("e1g1"), ucis.toString());
        assertTrue(ucis.contains("e1c1"), ucis.toString());
    }

    @Test
    @DisplayName("promotion generates all four pieces; a bare a7a8 applies as a queen")
    void promotion() {
        List<String> ucis = Rules.legalMoves(PROMOTION).stream().map(ChessMove::toUci).toList();
        assertTrue(ucis.contains("a7a8q"), ucis.toString());
        assertTrue(ucis.contains("a7a8r"));
        assertTrue(ucis.contains("a7a8b"));
        assertTrue(ucis.contains("a7a8n"));
        assertEquals('Q', Position.fromFen(Rules.applyMove(PROMOTION, ChessMove.fromUci("a7a8")))
                .pieceAt(Square.fromName("a8")));
    }

    @Test
    @DisplayName("KNOWN GAP (bitboard e.p. bug — increment 2): e5xf6 e.p. is not generated yet")
    void enPassantNotYetGenerated() {
        // BitPawn.getEnPassantMoves drops the capturing pawn instead of moving it, so the
        // adapter skips that malformed child (see BitBoardRules javadoc / research §2.4).
        List<String> ucis = Rules.legalMoves(EN_PASSANT).stream().map(ChessMove::toUci).toList();
        assertFalse(ucis.contains("e5f6"), "expected e.p. to be absent until increment 2: " + ucis);
        for (String u : ucis) {
            assertTrue(u.matches("[a-h][1-8][a-h][1-8][qrbn]?"), "malformed: " + u);
        }
    }

    // ---- status: check / mate / stalemate --------------------------------

    @Test
    @DisplayName("initial position: in progress, not check")
    void startStatus() {
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(START));
        assertFalse(Rules.isCheck(START));
    }

    @Test
    @DisplayName("fool's mate is checkmate")
    void foolsMate() {
        assertTrue(Rules.legalMoves(FOOLS_MATE).isEmpty());
        assertTrue(Rules.isCheckmate(FOOLS_MATE));
        assertEquals(GameStatus.CHECKMATE, Rules.status(FOOLS_MATE));
    }

    @Test
    @DisplayName("back-rank rook mate is checkmate")
    void backRankMate() {
        assertEquals(GameStatus.CHECKMATE, Rules.status(BACK_RANK_MATE));
    }

    @Test
    @DisplayName("K+Q vs K is stalemate, not mate")
    void stalemate() {
        assertTrue(Rules.legalMoves(STALEMATE).isEmpty());
        assertFalse(Rules.isCheck(STALEMATE));
        assertEquals(GameStatus.STALEMATE, Rules.status(STALEMATE));
    }

    @Test
    @DisplayName("a plain check is CHECK, game continues")
    void plainCheck() {
        String fen = "4r2k/8/8/8/8/8/8/4K3 w - - 0 1";
        assertTrue(Rules.isCheck(fen));
        assertEquals(GameStatus.CHECK, Rules.status(fen));
        assertFalse(Rules.legalMoves(fen).isEmpty());
    }

    // ---- status: the draw rules Phase 2 fixes ---------------------------

    @Test
    @DisplayName("DRAW FIXED: half-move clock is read in full, not truncated to one digit")
    void halfmoveClockFull() {
        assertEquals(50, Position.fromFen("k7/8/8/8/8/8/8/7K w - - 50 100").halfmoveClock());
        assertEquals(13, Position.fromFen("k7/8/8/8/8/8/8/7K w - - 13 30").halfmoveClock());
    }

    @Test
    @DisplayName("DRAW FIXED: 50-move rule fires at 100 plies, not 50, and not before")
    void fiftyMoveRule() {
        assertEquals(GameStatus.IN_PROGRESS, Rules.status("7k/8/8/8/8/8/1R6/K7 w - - 98 90"));
        assertEquals(GameStatus.DRAW_FIFTY_MOVE, Rules.status("7k/8/8/8/8/8/1R6/K7 w - - 100 90"));
    }

    @Test
    @DisplayName("DRAW FIXED: insufficient material — K vs K, K+minor vs K, K+B vs K+B same colour")
    void insufficientMaterial() {
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("k7/8/8/8/8/8/8/7K w - - 0 1"));
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("k7/8/8/8/8/8/8/6BK w - - 0 1"));
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("kn6/8/8/8/8/8/8/7K w - - 0 1"));
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("k1b5/8/8/8/8/8/6B1/7K w - - 0 1"));
        // a pawn is always enough
        assertNotEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("k7/7p/8/8/8/8/8/7K w - - 0 1"));
        // K+B+B (same side) can mate -> NOT insufficient
        assertNotEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, Rules.status("k7/8/8/8/8/8/5BB1/7K w - - 0 1"));
    }

    @Test
    @DisplayName("threefold repetition is a draw once the position recurs a 3rd time")
    void threefold() {
        Game g = new Game();
        for (String uci : new String[]{"g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8"}) {
            g.play(uci);
        }
        assertEquals(3, g.maxRepetitionCount());
        assertTrue(g.isThreefoldRepetition());
        assertEquals(GameStatus.DRAW_THREEFOLD, g.status());
    }

    // ---- end-to-end ------------------------------------------------------

    @Test
    @DisplayName("a full scripted game to Scholar's mate through the new API")
    void scriptedGame() {
        Game g = new Game();
        for (String uci : new String[]{"e2e4", "e7e5", "f1c4", "f8c5", "d1h5", "b8c6", "h5f7"}) {
            g.play(uci);
        }
        assertEquals(GameStatus.CHECKMATE, g.status());
    }

    @Test
    @DisplayName("applyMove: 1.e4 produces the expected FEN with en-passant target e3")
    void applyE4() {
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                Rules.applyMove(START, ChessMove.fromUci("e2e4")));
    }

    @Test
    @DisplayName("FEN round-trips through Position unchanged")
    void fenRoundTrip() {
        for (String fen : new String[]{START, FOOLS_MATE, BACK_RANK_MATE, STALEMATE,
                CASTLING_OPEN, EN_PASSANT, PROMOTION}) {
            assertEquals(fen, Position.fromFen(fen).toFen());
        }
    }

    @Test
    @DisplayName("UCI parse / format round-trips, including promotion")
    void uciRoundTrip() {
        for (String uci : new String[]{"e2e4", "a7a8q", "e1g1", "h7h8n"}) {
            assertEquals(uci, ChessMove.fromUci(uci).toUci());
        }
    }
}
