package ai.eval;

import ai.board.Boards;
import ai.board.PieceBoard;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7: the network's inputs, numbered the same way in Java and in tools/net/encode.py. */
class NetFeaturesTest {

    static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    static PieceBoard board(String fen) {
        return (PieceBoard) Boards.fromFen(Variants.CHESS, fen);
    }

    @Test
    @DisplayName("chess has 768 inputs; the start position turns 32 on, kings at e1 (own) and e8 (opponent)")
    void startPosition() {
        assertEquals(768, NetFeatures.inputs(Variants.CHESS));
        int[] on = NetFeatures.active(board(START));
        assertEquals(32, on.length);
        int[] sorted = on.clone();
        Arrays.sort(sorted);
        assertArrayEquals(sorted, on, "ascending");
        // King is type 0 (StandardPieces.ALL order), e1 is square 60, e8 is square 4
        assertTrue(Arrays.binarySearch(on, NetFeatures.index(Variants.CHESS, 0, 0, 60)) >= 0, "own king on e1");
        assertEquals(60, NetFeatures.index(Variants.CHESS, 0, 0, 60));
        assertEquals(6 * 64 + 4, NetFeatures.index(Variants.CHESS, 1, 0, 4));
        assertTrue(Arrays.binarySearch(on, 6 * 64 + 4) >= 0, "opponent's king on e8");
        // own pawn on e2 (square 52), pawn is type 5
        assertTrue(Arrays.binarySearch(on, 5 * 64 + 52) >= 0);
    }

    @Test
    @DisplayName("Black sees the board mirrored: the start position looks the same to both players")
    void blackViewMirrors() {
        assertArrayEquals(NetFeatures.active(board(START)), NetFeatures.active(board(START.replace(" w ", " b "))));
        // from Black's view e8 is 'e1' (square 60) and e1 is 'e8' (square 4)
        assertEquals(60, NetFeatures.fromSide(Variants.CHESS.grid(), 1, 4));
        assertEquals(4, NetFeatures.fromSide(Variants.CHESS.grid(), 1, 60));
        assertEquals(27, NetFeatures.fromSide(Variants.CHESS.grid(), 1, 35), "d4 (35) is Black's d5 (27)");
        assertEquals(35, NetFeatures.fromSide(Variants.CHESS.grid(), 0, 35));
    }

    @Test
    @DisplayName("a position and its colour-swapped mirror have the same inputs for the side to move")
    void colourSymmetry() {
        for (String fen : List.of(
                "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
                "r2q1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PP3PPP/R2Q1RK1 b - - 3 10",
                "8/5pk1/6p1/3P4/2p5/2P3P1/5PK1/8 w - - 0 40",
                "4r1k1/1b3ppp/p7/1p6/3N4/1P3Q2/P4PPP/3R2K1 b - - 1 25")) {
            assertArrayEquals(NetFeatures.active(board(fen)), NetFeatures.active(board(flip(fen))), fen);
        }
    }

    @Test
    @DisplayName("antichess numbers its inputs by its own piece list")
    void otherVariant() {
        assertEquals(2 * Variants.ANTICHESS.pieces().size() * 64, NetFeatures.inputs(Variants.ANTICHESS));
        PieceBoard board = (PieceBoard) Boards.fromFen(Variants.ANTICHESS, Variants.ANTICHESS.startFen());
        assertEquals(32, NetFeatures.active(board).length);
    }

    /** The same position with the colours swapped and the board mirrored, the other side to move. */
    static String flip(String fen) {
        String[] f = fen.split(" ");
        String[] ranks = f[0].split("/");
        StringBuilder placement = new StringBuilder();
        for (int i = ranks.length - 1; i >= 0; i--) {
            for (char c : ranks[i].toCharArray()) {
                placement.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
            }
            if (i > 0) {
                placement.append('/');
            }
        }
        StringBuilder castling = new StringBuilder();
        for (char c : f[2].toCharArray()) {
            castling.append(c == '-' ? c : Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return placement + " " + (f[1].equals("w") ? "b" : "w") + " " + castling + " " + f[3] + " " + f[4] + " " + f[5];
    }
}
