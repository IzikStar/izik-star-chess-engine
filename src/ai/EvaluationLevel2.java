package ai;

import main.Board;
import main.setting.ChoosePlayFormat;
import pieces.Piece;

public class EvaluationLevel2 {

    public static int evaluate(BoardState board) {
        int score = 0;

        // דוגמה להערכת מצב על הלוח
        for (Piece piece : board.getAllPieces()) {
            if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                score += getPieceValue(piece);
            } else {
                score -= getPieceValue(piece);
            }
        }

        return score;
    }

    private static int getPieceValue(Piece piece) {
        return (int) piece.value;
    }

}
