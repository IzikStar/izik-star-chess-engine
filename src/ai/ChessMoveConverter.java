package ai;

import main.Move;
import main.Board;
import pieces.Piece;

public class ChessMoveConverter {

    public static Move convertChessNotationToMove(Board board, Piece piece, String chessMove) {
        if (chessMove == null || chessMove.length() != 4) {
            throw new IllegalArgumentException("Invalid chess move notation");
        }

        // Extract start and end positions from the chess move notation
        char startFile = chessMove.charAt(0);  // e.g., 'e'
        char startRank = chessMove.charAt(1);  // e.g., '2'
        char endFile = chessMove.charAt(2);    // e.g., 'e'
        char endRank = chessMove.charAt(3);    // e.g., '4'

        // Convert file (a-h) to column index (0-7)
        int startX = startFile - 'a';
        int endX = endFile - 'a';

        // Convert rank (1-8) to row index (7-0) because the matrix is inverted
        int startY = 8 - Character.getNumericValue(startRank);
        int endY = 8 - Character.getNumericValue(endRank);

        // Ensure the piece is at the starting position
        if (piece.col != startX || piece.row != startY) {
            throw new IllegalArgumentException("Piece position does not match the starting position of the move");
        }

        // Create and return the Move object
        return new Move(board, piece, endX, endY);
    }

    public static void main(String[] args) {
        Board board = new Board();
        Piece piece = board.getPiece(4, 6); // Example: get the piece at e2

        String chessMove = "e2e4";
        Move move = convertChessNotationToMove(board, piece, chessMove);
        System.out.println("Move created: " + move);
    }
}

