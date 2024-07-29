package main;

import ai.BitBoard.BitMove;
import ai.BitBoard.BitOperations;
import ai.BoardState;
import pieces.*;

public class Move {

    int oldCol;
    int oldRow;
    public int newCol;
    public int newRow;

    public Piece piece;
    public Piece captured;

    public Move(BoardState board, Piece piece, int newCol, int newRow) {
        this.newCol = newCol;
        this.newRow = newRow;
        this.oldCol = piece.col;
        this.oldRow = piece.row;

        this.piece = piece;
        this.captured = board.getPiece(newCol, newRow);
    }

    public Move(BoardState board, BitMove move) {
        long from = (move.piece.position & ~(move.piece.position & move.newPosition));
        oldCol = BitOperations.getColIndexFromBit(from);
        oldRow = BitOperations.getRowIndexFromBit(from);
        long to = (move.newPosition & ~(move.piece.position & move.newPosition));
        newCol = BitOperations.getColIndexFromBit(to);
        newRow = BitOperations.getRowIndexFromBit(to);
        switch (move.piece.name) {
            case 1:
                this.piece = new King(board, oldCol, oldRow, move.piece.color == 1);
                break;
            case 2:
                this.piece = new Queen(board, oldCol, oldRow, move.piece.color == 1);
                break;
            case 3:
                this.piece = new Rook(board, oldCol, oldRow, move.piece.color == 1);
                break;
            case 4:
                this.piece = new Bishop(board, oldCol, oldRow, move.piece.color == 1);
                break;
            case 5:
                this.piece = new Knight(board, oldCol, oldRow, move.piece.color == 1);
                break;
            case 6:
                this.piece = new Pawn(board, oldCol, oldRow, move.piece.color == 1, 8);
                break;
            default: break;
        }
        captured = board.getPiece(newCol, newRow);
    }

    public Piece getPiece() {
        return piece;
    }

    @Override
    public String toString() {
        return "[" + oldCol + ", " + oldRow + " to " + newCol + ", " + newRow + "]";
    }


//    public int getRow() {
//        return row;
//    }
//
//    public int getCol() {
//        return col;
//    }

}
