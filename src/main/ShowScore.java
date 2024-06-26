package main;

import pieces.*;

public class ShowScore {
    Board board;
    public double blackScore; // = King.value + Queen.value + 2 * Rook.value + 2 * Knight.value + 2 * Bishop.value + 8 * Pawn.value;
    public double whiteScore; // = King.value + Queen.value + 2 * Rook.value + 2 * Knight.value + 2 * Bishop.value + 8 * Pawn.value;


    public ShowScore(Board board) {
        this.board = board;
        // calculateScore();
    }
    public double calculateScore() {
        whiteScore = 0;
        blackScore = 0;
        for (Piece piece : board.pieceList) {
            if (piece != null) {
                if (piece.isWhite) {
                    whiteScore += piece.value;
                } else {
                    blackScore += piece.value;
                }
            }
        }
//        if (blackScore > whiteScore) {
//             System.out.println("black is better.");
//        } else if (whiteScore > blackScore) {
//             System.out.println("white is better.");
//        }
        // System.out.println(Math.abs(blackScore - whiteScore));
        return Math.abs(blackScore - whiteScore);
    }
}
