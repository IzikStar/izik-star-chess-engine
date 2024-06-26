package main;

import pieces.Piece;

public class Move {

    int oldCol;
    int oldRow;
    int newCol;
    int newRow;

    Piece piece;
    Piece captured;

    public Move(Board board, Piece piece, int newCol, int newRow) {
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


//    public int getRow() {
//        return row;
//    }
//
//    public int getCol() {
//        return col;
//    }

}
