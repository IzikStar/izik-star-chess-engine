package pieces;

import main.Board;
import main.setting.ChoosePlayFormat;

import java.awt.image.BufferedImage;

public class Bishop extends Piece{
    public Bishop(Board board, int col, int row, boolean isWhite) {
        super(board);
        this.col = col;
        this.row = row;
        if (ChoosePlayFormat.isPlayingWhite) {
            this.xPos = col * board.tileSize;
            this.yPos = row * board.tileSize;
        }
        else {
            this.xPos = (7 - col) * board.tileSize;
            this.yPos = (7 - row) * board.tileSize;
        }

        this.isWhite = isWhite;
        this.name = "Bishop";
        this.type = isWhite ? 'B' : 'b';
        this.value = 3;

        this.sprite = sheet.getSubimage(2 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(board.tileSize, board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        return Math.abs(col - this.col) == Math.abs(row - this.row);
    }

    public boolean moveCollidesWithPiece(int col, int row) {

        // up left
        if (this.col > col && this.row > row) {
            for (int i = 1; i < Math.abs(col - this.col); i++) {
                if (board.getPiece(this.col - i, this.row - i) != null) {
                    return true;
                }
            }
        }

        // up right
        if (this.col < col && this.row > row) {
            for (int i = 1; i < Math.abs(col - this.col); i++) {
                if (board.getPiece(this.col + i, this.row - i) != null) {
                    return true;
                }
            }
        }

        // down left
        if (this.col > col && this.row < row) {
            for (int i = 1; i < Math.abs(col - this.col); i++) {
                if (board.getPiece(this.col - i, this.row + i) != null) {
                    return true;
                }
            }
        }

        // up right
        if (this.col < col && this.row < row) {
            for (int i = 1; i < Math.abs(col - this.col); i++) {
                if (board.getPiece(this.col + i, this.row + i) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
