package ai.BitBoard;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ZobristHashing {
    private static final long[][][] zobristTable = new long[2][7][64]; // [צבע][סוג כלי][משבצת]

    static {
        Random random = new Random(0);
        for (int color = 0; color < 2; color++) {
            for (int piece = 0; piece < 6; piece++) {
                for (int square = 0; square < 64; square++) {
                    zobristTable[color][piece][square] = random.nextLong();
                }
            }
        }
    }

    public static Map<Long, Integer> boardStateMap = new HashMap<>();
    public static long computeHash(BitBoard board) {
        long hash = 0L;

        for (int square = 0; square < 64; square++) {
            int piece = board.getPieceAt(square); // תניח שיש פונקציה שמחזירה את סוג הכלי במיקום זה
            if (piece != 0) {
                int color = board.getColorAt(square); // תניח שיש פונקציה שמחזירה את צבע הכלי במיקום זה
                hash ^= zobristTable[color][piece][square];
            }
        }

        return hash;
    }
}

