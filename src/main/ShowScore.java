package main;

import pieces.*;

public class ShowScore {
    Board board;
    public static int blackScore; // = King.value + Queen.value + 2 * Rook.value + 2 * Knight.value + 2 * Bishop.value + 8 * Pawn.value;
    public static int whiteScore; // = King.value + Queen.value + 2 * Rook.value + 2 * Knight.value + 2 * Bishop.value + 8 * Pawn.value;


    public ShowScore(Board board) {
        this.board = board;
    }

    public double calculateScore() {
        whiteScore = 0;
        blackScore = 0;
        int betterScore = 0;
        for (Piece piece : board.pieceList) {
            if (piece != null) {
                if (piece.isWhite) {
                    whiteScore += piece.value;
                } else {
                    blackScore += piece.value;
                }
            }
        }
        betterScore = Math.abs(blackScore - whiteScore);
        if (blackScore > whiteScore) {
             //System.out.println("black is better.");
            Main.updateScores(-betterScore, betterScore);
        } else if (whiteScore > blackScore) {
             //System.out.println("white is better.");
            Main.updateScores(betterScore, -betterScore);
        } else {
            Main.updateScores(0, 0);
        }
        // System.out.println(Math.abs(blackScore - whiteScore));
        return betterScore;
    }
}
