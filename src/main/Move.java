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
        piece = board.getPiece(oldCol, oldRow);
        if (piece == null) System.out.println("!!!!!!!");
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
