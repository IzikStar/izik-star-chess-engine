package ai.BitBoard;

import ai.BoardState;

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

    public static void main(String[] args) {
        BitBoard board = new BitBoard(new BoardState("\"rnbqkbnr/1p1p1ppp/2p5/p3p3/2B1P3/3P4/PPP2PPP/RNBQK1NR w qkQK a6 0 3", null));
        long hash = computeHash(board);
        System.out.println(hash);
        BitBoard board2 = new BitBoard(new BoardState("\"rnbqk1nr/1p1p1ppp/2p5/p3p3/2B1P3/3P4/PPP2PPP/RNBQK1NR w qkQK a6 0 3", null));
        long hash2 = computeHash(board);
        System.out.println(hash2);
        BitBoard board3 = new BitBoard(new BoardState("\"rn4nr/1p1p1ppp/2p5/p3p3/2B1P3/3P4/PPP2PPP/RNBQK1NR w qkQK a6 0 3", null));
        long hash3 = computeHash(board);
        System.out.println(hash3);
    }
}

