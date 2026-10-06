package lab;

import ai.board.Boards;
import ai.board.PieceBoard;
import ai.eval.NetFeatures;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7: the training file, everything from the side to move. */
class NetDataTest {

    static final String WHITE_TO_MOVE = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4";
    static final String BLACK_TO_MOVE = "r2q1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PP3PPP/R2Q1RK1 b - - 3 10";
    static final String ENDGAME = "8/5pk1/6p1/3P4/2p5/2P3P1/5PK1/8 w - - 0 40";

    @Test
    @DisplayName("rows hold the inputs, the result and the score for the side to move; 32 slots for chess")
    void roundTrip(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("p.csv"), bin = dir.resolve("p.bin");
        Files.writeString(csv, TrainingExport.HEADER + "\n"
                + WHITE_TO_MOVE + ",1,35\n"
                + BLACK_TO_MOVE + ",1,35\n"
                + ENDGAME + ",0.5,\n");
        assertEquals(32, NetData.slots(Variants.CHESS));
        assertEquals(3, NetData.write(Variants.CHESS, csv, bin));
        assertEquals(NetData.HEADER_BYTES + 3 * NetData.rowBytes(32), Files.size(bin));

        List<NetData.Row> rows = NetData.read(bin);
        assertEquals(3, rows.size());
        // White to move: as the file says
        assertArrayEquals(NetFeatures.active((PieceBoard) Boards.fromFen(Variants.CHESS, WHITE_TO_MOVE)), rows.get(0).active());
        assertEquals(1f, rows.get(0).result());
        assertEquals(35f, rows.get(0).score());
        assertEquals(0, rows.get(0).sideToMove());
        // Black to move: White's win is Black's loss, White's +35 is Black's -35
        assertEquals(0f, rows.get(1).result());
        assertEquals(-35f, rows.get(1).score());
        assertEquals(1, rows.get(1).sideToMove());
        assertArrayEquals(NetFeatures.active((PieceBoard) Boards.fromFen(Variants.CHESS, BLACK_TO_MOVE)), rows.get(1).active());
        // no score in the file: NaN; fewer pieces than slots: the rest is padding
        assertEquals(0.5f, rows.get(2).result());
        assertTrue(Float.isNaN(rows.get(2).score()));
        assertEquals(9, rows.get(2).active().length);
    }
}
