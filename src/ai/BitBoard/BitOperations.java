package ai.BitBoard;


public class BitOperations {
    // Set a bit at a specific position
    public static long setBit(long bitboard, int position) {
        return bitboard | (1L << position);
    }
    // Clear a bit at a specific position
    public static long clearBit(long bitboard, int position) {
        return bitboard & ~(1L << position);
    }
    // Toggle a bit at a specific position
    public static void toggleBit(long bitboard, int position) {
        bitboard ^= (1L << position);
    }
    // Check if a bit at a specific position is set
    public static boolean isBitSet(long bitboard, int position) {
        return (bitboard & (1L << position)) != 0;
    }

    // Method to check if the result has exactly two 1-bits that are 16 positions apart
    public static boolean isShiftBy16(long bitboard) {
        // Check if the number has exactly two 1-bits
        if (Long.bitCount(bitboard) != 2) {
            return false;
        }

        // Check if the number is of the form 1 << x | 1 << (x + 16)
        while (bitboard != 0 && (bitboard & 1) == 0) {
            bitboard >>>= 1;
        }

        // Now the least significant bit is set, shift right by 16 and check the next bit
        return (bitboard >> 1) == (1L << 15);
    }

    // Print the bitboard as a binary string
    public static int bitToInt(long bitBoard, int position) {
        return isBitSet(bitBoard, position) ? 1 : 0;
    }
    public static char bitToChar(long bitBoard, int position) {
        return isBitSet(bitBoard, position) ? '*' : '-';
    }
    public static String printBitboard(long bitboard) {
        char[][] board = new char[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = bitToChar(bitboard, (i) * 8 + (j));
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        for (int i = 0; i < 8; i++) {
            stringBuilder.append("[ ");
            for (int j = 0; j < 8; j++) {
                stringBuilder.append(board[i][j]).append(" ");
            }
            stringBuilder.append("]\n");
        }
        return String.valueOf((stringBuilder));
    }

    // count the on bits in a binary long
    public static int countSetBits(long bitboard) {
        int count = 0;
        while (bitboard != 0) {
            count += (int) (bitboard & 0x1L);
            bitboard >>>= 1;
        }
        return count;
    }

    public static int getRowFromBit(long bitBoard) {
        if ((bitBoard & BoardParts.FIRST_RANK) != 0) return 1;
        if ((bitBoard & BoardParts.SECOND_RANK) != 0) return 2;
        if ((bitBoard & BoardParts.THIRD_RANK) != 0) return 3;
        if ((bitBoard & BoardParts.FOURTH_RANK) != 0) return 4;
        if ((bitBoard & BoardParts.FIFTH_RANK) != 0) return 5;
        if ((bitBoard & BoardParts.SIXTH_RANK) != 0) return 6;
        if ((bitBoard & BoardParts.SEVENTH_RANK) != 0) return 7;
        if ((bitBoard & BoardParts.EIGHTH_RANK) != 0) return 8;
        return 0;
    }
    public static int getColFromBit(long bitBoard) {
        if ((bitBoard & BoardParts.A_FILE) != 0) return 1;
        if ((bitBoard & BoardParts.B_FILE) != 0) return 2;
        if ((bitBoard & BoardParts.C_FILE) != 0) return 3;
        if ((bitBoard & BoardParts.D_FILE) != 0) return 4;
        if ((bitBoard & BoardParts.E_FILE) != 0) return 5;
        if ((bitBoard & BoardParts.F_FILE) != 0) return 6;
        if ((bitBoard & BoardParts.G_FILE) != 0) return 7;
        if ((bitBoard & BoardParts.H_FILE) != 0) return 8;
        return 0;
    }

    public static int getRowIndexFromBit(long bitBoard) {
        if ((bitBoard & BoardParts.FIRST_RANK) != 0) return 7;
        if ((bitBoard & BoardParts.SECOND_RANK) != 0) return 6;
        if ((bitBoard & BoardParts.THIRD_RANK) != 0) return 5;
        if ((bitBoard & BoardParts.FOURTH_RANK) != 0) return 4;
        if ((bitBoard & BoardParts.FIFTH_RANK) != 0) return 3;
        if ((bitBoard & BoardParts.SIXTH_RANK) != 0) return 2;
        if ((bitBoard & BoardParts.SEVENTH_RANK) != 0) return 1;
        if ((bitBoard & BoardParts.EIGHTH_RANK) != 0) return 0;
        return 0;
    }
    public static int getColIndexFromBit(long bitBoard) {
        if ((bitBoard & BoardParts.A_FILE) != 0) return 0;
        if ((bitBoard & BoardParts.B_FILE) != 0) return 1;
        if ((bitBoard & BoardParts.C_FILE) != 0) return 2;
        if ((bitBoard & BoardParts.D_FILE) != 0) return 3;
        if ((bitBoard & BoardParts.E_FILE) != 0) return 4;
        if ((bitBoard & BoardParts.F_FILE) != 0) return 5;
        if ((bitBoard & BoardParts.G_FILE) != 0) return 6;
        if ((bitBoard & BoardParts.H_FILE) != 0) return 7;
        return 0;
    }

}
