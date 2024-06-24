package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Pawn extends Piece{

    public int id;

    public Pawn(Board board, int col, int row, boolean isWhite, int id) {
        super(board);
        this.col = col;
        this.row = row;
        this.xPos = col * board.tileSize;
        this.yPos = row * board.tileSize;

        this.isWhite = isWhite;
        this.name = "Pawn";
        this.type = isWhite ? 'P' : 'p';
        this.id = id;
        this.sprite = sheet.getSubimage(5 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(board.tileSize, board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        int colorIndex = isWhite ? 1 : -1;

        // push pawn 1:
        if (this.col == col && this.row == row + colorIndex && board.getPiece(col, row) == null)
            return true;

        // push pawn 2:
        if (this.col == col && this.row == row + 2 * colorIndex && this.isFirstMove && board.getPiece(col, row) == null && board.getPiece(col, row + colorIndex) == null)
            return true;

        // capture:
        if (Math.abs(this.col - col) == 1 && this.row  == row + colorIndex && board.getPiece(col, row) != null)
            return true;

        // en passant:
        if (board.getTileNum(col, row) == board.enPassantTile && Math.abs(col - this.col) == 1 && row == this.row - colorIndex && board.getPiece(col, row + colorIndex) != null) {
            return true;
        }


        return false;
    }


}
