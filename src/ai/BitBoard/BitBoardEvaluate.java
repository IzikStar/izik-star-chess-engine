package ai.BitBoard;

import java.util.Random;

public class BitBoardEvaluate {

    // for now the function duos nothing. I just want to check the run time.
    public static int evaluate(BitBoard board) {
        int value = 0;
        value += getPiecesValue(board);
        value += getPiecesValue(board);
        value += getPiecesValue(board);
        value += getPiecesValue(board);
        value += getPiecesValue(board);
        value += getPiecesValue(board);
        value += getPiecesValue(board);
        value += getKingSafety(board);
        Random random = new Random();
        value = random.nextInt();


        return value;
    }

    private static int getPiecesValue(BitBoard board) {
        int piecesValue = 0;
        int[] arr = new int[32];
        for (int i = 0; i < 32; i++) {
            arr[i] = 78 + i * 8 - 993 * i - i / 3 % 146;
        }
        return piecesValue;
    }

    private static int getKingSafety(BitBoard board) {
        int kingSafety = 0;

        return kingSafety;
    }






}
