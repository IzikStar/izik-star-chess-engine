package main;

import ai.BitBoard.BitMove;
import ai.BitBoard.BitOperations;
import ai.BoardState;
import pieces.*;

import java.util.ArrayList;

public class Move {

    int oldCol;
    int oldRow;
    public int newCol;
    public int newRow;
    public int num;
    public String representation;
    BoardState board;
    public Piece piece;
    public Piece captured;
    private char promotionChoice;

    public Move(BoardState board, Piece piece, int newCol, int newRow) {
        this.newCol = newCol;
        this.newRow = newRow;
        this.oldCol = piece.col;
        this.oldRow = piece.row;
        this.num = board.numOfTurns;
        this.board = board;

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

        this.board = board;
        this.num = board.numOfTurns;
    }

    public Piece getPiece() {
        return piece;
    }



    public void setPromotionChoice(char promotionChoice) {
        this.promotionChoice = promotionChoice;
        representation = representation + "=" + Character.toUpperCase(promotionChoice);
    }

    public void setRepresentation() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(piece.isWhite ? "\n" : " ");
        if (piece.isWhite) {
            stringBuilder.append(board.numOfTurns).append(". ");
        }

        String castling = getCastlingString();
        stringBuilder.append(castling);

        String status = getStatusString();
        if (!castling.isEmpty()) {
            stringBuilder.append(status);
            representation = stringBuilder.toString();
            return;
        }


        String piece = getPieceString();
        stringBuilder.append(piece);
        String capture = getCaptureString();
        stringBuilder.append(capture);
        stringBuilder.append(board.squareToLetters(newCol, newRow).toLowerCase());
        String promotion = getPromotionString();
        stringBuilder.append(promotion);
        String enPassant = getEnPassantString();
        stringBuilder.append(enPassant);
        stringBuilder.append(status);
        representation = stringBuilder.toString();
    }

    public String getRepresentation() {
        return representation;
    }

    private String getPieceString() {
        String piece = Character.toString(Character.toUpperCase(this.piece.type));
        StringBuilder result = new StringBuilder();
        Piece[] allPieces = board.getAllPieces();
        int relevantPieces = 0;
        for (Piece p : allPieces) {
            if (p != this.piece && p.isWhite == this.piece.isWhite && (p.name.equals(this.piece.name))) {
                if (board.isValidMove(new Move(board, p, newCol, newRow))) {
                    if (p.col != this.piece.col || p.row != this.piece.row) {
                        result.append(piece);
                        relevantPieces++;
                        if (p.col != this.piece.col) result.append(board.colToLetter(this.piece.col));
                        else result.append(this.piece.row + 1);
                    }
                }
            }
        }
        if (relevantPieces == 0 && !piece.equals("P")) {
            result.append(piece);
        }
        if (piece.equals("P") && captured != null && relevantPieces == 0) {
            result.append(Character.toLowerCase(board.colToLetter(oldCol).charAt(0)));
        }
        return result.toString();
    }
    private String getEnPassantString() {
        if (piece instanceof Pawn && Math.abs(piece.col - newCol) == 1 && board.getPiece(newCol, newRow) == null) return " .e.p";
        return "";
    }
    private String getPromotionString() {
        if (promotionChoice != 0) {
            return "=" + Character.toUpperCase(promotionChoice);
        }
        return "";
    }
    private String getCaptureString() {
        if (captured != null) return "x";
        return "";
    }
    private String getStatusString() {
        int status = board.makeMoveAndGetStatus(this);
        if (status == 0) return "\n1/2-1/2";
        if (status == Integer.MAX_VALUE) return "#" + (piece.isWhite ? "\n1-0" : "\n0-1");
        if (status == 2) return "+";
        return "";
    }
    private String getCastlingString() {
        if (piece instanceof King && Math.abs(oldCol - newCol) == 2) {
            if (newCol == 6) return "O-O";
            return "O-O-O";
        }
        return "";
    }

}
