package ai.BitBoard;

import ai.BitBoard.BitPiece.BitPiece;

public class BitMove {
    public BitPiece piece;
    public long newPosition;
    // capturing, promotion, enPassant or castling all require one more long to change
    public BitPiece otherPieceToChange;
    public boolean isCastling = false;
    public boolean isEnPassant = false;
    public String promotionChoice;

    public BitMove(BitPiece piece, long newPosition){
        this.piece = piece;
        this.newPosition = newPosition;
    }
    public BitMove(BitPiece piece, long newPosition, BitPiece otherPieceToChange){
        this.piece = piece;
        this.newPosition = newPosition;
        this.otherPieceToChange = otherPieceToChange;
    }
    public BitMove(BitPiece piece, long newPosition, String promotionChoice, BitPiece otherPieceToChange){
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

}
