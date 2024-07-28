package ai.BitBoard;

public class BitBoardOperations {


    // Print the bitboard as a binary string
    public static char bitToChar(BitBoard bitBoard, int position) {
        char ch = '-';
        if (BitOperations.isBitSet(bitBoard.blackKings, position)) ch = 'k';
        else if (BitOperations.isBitSet(bitBoard.blackQueens, position)) ch = 'q';
        else if (BitOperations.isBitSet(bitBoard.blackRooks, position)) ch = 'r';
        else if (BitOperations.isBitSet(bitBoard.blackBishops, position)) ch = 'b';
        else if (BitOperations.isBitSet(bitBoard.blackKnights, position)) ch = 'n';
        else if (BitOperations.isBitSet(bitBoard.blackPawns, position)) ch = 'p';
        else if (BitOperations.isBitSet(bitBoard.whiteKings, position)) ch = 'K';
        else if (BitOperations.isBitSet(bitBoard.whiteQueens, position)) ch = 'Q';
        else if (BitOperations.isBitSet(bitBoard.whiteRooks, position)) ch = 'R';
        else if (BitOperations.isBitSet(bitBoard.whiteBishops, position)) ch = 'B';
        else if (BitOperations.isBitSet(bitBoard.whiteKnights, position)) ch = 'N';
        else if (BitOperations.isBitSet(bitBoard.whitePawns, position)) ch = 'P';
        return ch;
    }
    public static String printBitBoard(BitBoard bitboard) {
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

    // switch the color from white to black:
    public static int toggleColor(int color) {
        return color == 1 ? 0 : 1;
    }

}
