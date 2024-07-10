package main;

import ai.BoardState;
import pieces.Piece;

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

    public Piece getPiece() {
        return piece;
    }

    @Override
    public String toString() {
        return oldCol + ", " + oldRow + " to " + newCol + ", " + newRow;
    }


//    public int getRow() {
//        return row;
//    }
//
//    public int getCol() {
//        return col;
//    }

}
