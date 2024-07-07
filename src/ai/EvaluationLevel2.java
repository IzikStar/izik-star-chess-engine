package ai;

import main.Board;
import main.setting.ChoosePlayFormat;
import pieces.Piece;

public class EvaluationLevel2 {

    public static double evaluate(BoardState board) {
        double score = 0;

        // דוגמה להערכת מצב על הלוח
        for (Piece piece : board.getAllPieces()) {
            if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                score += getPieceValue(piece);
                score += piece.getValidMoves(board).size() * 0.25 * (piece.name.equals("King") ? 0 : getPieceValue(piece));
            } else {
                score -= getPieceValue(piece);
                score += piece.getValidMoves(board).size() * 0.25 * (piece.name.equals("King") ? 0 : getPieceValue(piece));
            }
        }



        return score;
    }

    private static int getPieceValue(Piece piece) {
        return (int) piece.value;
    }

}
