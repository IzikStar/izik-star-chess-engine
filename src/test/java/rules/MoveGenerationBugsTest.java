package rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The move-generator bugs a player can see, found by the Phase 4b perft suite
 * (docs/phase-4b-research.md §2.3), one test per bug. Expected moves are Stockfish's.
 * Bug #7 is not visible here; {@code ai.BitBoard.SearchEnPassantTest} covers it.
 */
class MoveGenerationBugsTest {

    static Set<String> legal(String fen) {
        Set<String> moves = new TreeSet<>();
        for (ChessMove m : Rules.legalMoves(fen)) {
            moves.add(m.toUci());
        }
        return moves;
    }

    static String after(String fen, String... moves) {
        for (String m : moves) {
            fen = Rules.applyMove(fen, ChessMove.fromUci(m));
        }
        return fen;
    }

    @Test
    @DisplayName("#1 a knight attacks all eight of its squares (Kf2 is no escape from Nd3+)")
    void knightAttacksAllEightSquares() {
        String fen = "4k3/8/8/8/8/3n4/8/4K3 w - - 0 1";
        assertTrue(Rules.isCheck(fen));
        assertEquals(Set.of("e1d1", "e1d2", "e1e2", "e1f1"), legal(fen));
    }

    @Test
    @Tag("known-bug")
    @DisplayName("#2 pawns attack diagonally: pawns give check, and kings can't walk into pawns")
    void pawnsAttack() {
        assertTrue(Rules.isCheck("4k3/3P4/8/8/8/8/8/4K3 b - - 0 1"), "white pawn d7 checks e8");
        assertEquals(Set.of("d6c5", "d6c6", "d6c7", "d6d7", "d6e5", "d6e6", "d6e7"),
                legal("8/8/3k4/8/2P5/8/8/4K3 b - - 0 1"), "white pawn c4 guards d5");
        assertTrue(Rules.isCheck("4k3/8/8/8/8/8/5p2/4K3 w - - 0 1"), "black pawn f2 checks e1");
        assertEquals(Set.of("a5a4", "a5a6", "a5b4", "a5b5", "a5b6"),
                legal("7k/7p/8/K7/8/8/8/8 w - - 0 1"), "black pawn h7 attacks g6 only, not the a-file");
    }

    @Test
    @Tag("known-bug")
    @DisplayName("#3 an en-passant capture that exposes the own king along the rank is illegal")
    void enPassantCannotExposeTheKing() {
        assertEquals(Set.of("a5a4", "a5a6", "a5b6", "b5b6"),
                legal(after("8/2p5/8/KP5r/8/8/8/7k b - - 0 1", "c7c5")));
    }

    @Test
    @Tag("known-bug")
    @DisplayName("#4 moving the a1 rook keeps the right to castle king side")
    void queenRookMoveKeepsKingSideCastling() {
        Set<String> moves = legal(after("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", "a1b1", "e8d8"));
        assertTrue(moves.contains("e1g1"), moves.toString());
    }

    @Test
    @Tag("known-bug")
    @DisplayName("#5 castling queen side is legal while b1 is attacked (only the king's path must be safe)")
    void queenSideCastlingWithB1Attacked() {
        assertTrue(legal("1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1").contains("e1c1"));
        assertTrue(legal("r3k3/8/8/8/8/8/8/1R2K3 b q - 0 1").contains("e8c8"));
    }

    @Test
    @Tag("known-bug")
    @DisplayName("#6 with two queens, each queen can still move sideways")
    void twoQueensMoveSideways() {
        Set<String> moves = legal("4k3/8/8/8/8/8/8/Q2QK3 w - - 0 1");
        assertTrue(moves.containsAll(Set.of("a1b1", "a1c1", "d1c1", "d1b1")), moves.toString());
        assertEquals(36, moves.size(), moves.toString());
    }

    @Test
    @DisplayName("#8 a king attacks all eight neighbours (the kings can't stand side by side)")
    void kingsCannotTouch() {
        assertEquals(Set.of("b6a5", "b6a6", "b6b5", "b6c5", "b6c6"), legal("1k6/8/1K6/8/8/8/8/8 w - - 0 1"));
        assertEquals(Set.of("d4c3", "d4c4", "d4d3", "d4d5", "d4e3", "d4e4", "d4e5"),
                legal("8/8/1K6/8/3k4/8/8/8 b - - 0 1"));
    }

    @Test
    @Tag("known-bug")
    @DisplayName("#9 a castling right ends when its rook is captured, even if another rook takes its place")
    void capturedRookEndsCastlingRight() {
        String black = after("r3k2r/7r/8/8/8/8/1B6/4K3 w kq - 0 1", "b2h8", "h7h8", "e1e2");
        assertFalse(legal(black).contains("e8g8"), black);
        assertTrue(legal(black).contains("e8c8"), black);
        String white = after("4k3/8/8/8/8/7R/6b1/4K2R b K - 0 1", "g2h1", "h3h1", "e8d8");
        assertFalse(legal(white).contains("e1g1"), white);
    }
}
