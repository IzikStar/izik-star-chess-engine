package ai.BitBoard;

import ai.BitBoard.BitPiece.BitPiece;
import main.Move;

public class BitMove {
    public BitPiece piece;
    public long newPosition;
    // capturing, promotion, enPassant or castling all require one more long to change
    public BitPiece otherPieceToChange = null;
    public boolean isCastling = false;
    public boolean isEnPassant = false;
    public char promotionChoice = 'q';

    public BitMove(BitPiece piece, long newPosition){
        this.piece = piece;
        this.newPosition = newPosition;
    }
    public BitMove(BitPiece piece, long newPosition, BitPiece otherPieceToChange){
        this.piece = piece;
        this.newPosition = newPosition;
        this.otherPieceToChange = otherPieceToChange;
    }
    public BitMove(BitPiece piece, long newPosition, char promotionChoice, BitPiece otherPieceToChange){
        this.piece = piece;
        this.newPosition = newPosition;
        this.promotionChoice = promotionChoice;
        this.otherPieceToChange = otherPieceToChange;
    }
    public BitMove(BitPiece piece, long newPosition, boolean isCastling, boolean isEnPassant, BitPiece otherPieceToChange){
        this.piece = piece;
        this.newPosition = newPosition;
        this.isCastling = isCastling;
        this.isEnPassant = isEnPassant;
        this.otherPieceToChange = otherPieceToChange;
    }

    public BitMove(long prevPosition, Move lastMove, int fromC, int fromR, boolean isCastling, boolean isPawnMove) {

    }

    public BitMove(BitMove move) {
        this.piece = move.piece;
        this.newPosition = move.newPosition;
        this.isCastling = move.isCastling;
        this.isEnPassant = move.isEnPassant;
        this.otherPieceToChange = move.otherPieceToChange;
        this.promotionChoice = move.promotionChoice;
    }

    public void setPromotionChoice(char promotionChoice) {
        this.promotionChoice = promotionChoice;
    }

    @Override
    public String toString() {
        return "piece: " + piece + ".\nfrom: " + BitOperations.printBitboard(piece.position) + "to: " + BitOperations.printBitboard(newPosition);
    }

}
