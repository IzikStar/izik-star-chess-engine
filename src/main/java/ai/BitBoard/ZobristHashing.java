package ai.BitBoard;


import java.util.Random;

public class ZobristHashing {
    private static final long[][][] zobristTable = new long[2][7][64]; // [צבע][סוג כלי][משבצת]
    private static final long BLACK_TO_MOVE;

    static {
        Random random = new Random(0);
        for (int color = 0; color < 2; color++) {
            for (int piece = 1; piece <= 6; piece++) {
                for (int square = 0; square < 64; square++) {
                    zobristTable[color][piece][square] = random.nextLong();
                }
            }
        }
        BLACK_TO_MOVE = random.nextLong();
    }

    /**
     * Position hash for the search's repetition check: piece placement + side to move. Before
     * Phase 3 pawns hashed as "empty" and the side to move was ignored, so any two pawn moves
     * looked like a threefold repetition and the search scored them as one.
     */
    public static long computeHash(BitBoard board) {
        long hash = board.getIsWhiteToMove() ? 0L : BLACK_TO_MOVE;

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
