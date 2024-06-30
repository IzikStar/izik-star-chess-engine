package pieces;

import main.Board;
import main.setting.ChoosePlayFormat;

import java.awt.image.BufferedImage;

public class Queen extends Piece{
    public Queen(Board board, int col, int row, boolean isWhite) {
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
        this.name = "Queen";
        this.type = isWhite ? 'Q' : 'q';
        this.value = 9;

        this.sprite = sheet.getSubimage(1 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(board.tileSize, board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        return this.col == col || this.row == row || Math.abs(col - this.col) == Math.abs(row - this.row);
    }

    public boolean moveCollidesWithPiece(int col, int row) {

        if (this.col == col || this.row == row) {
            // left
            if (this.col > col) {
                for (int c = this.col - 1; c > col; c--) {
                    if (board.getPiece(c, this.row) != null) {
                        return true;
                    }
                }
            }

            // right
            if (this.col < col) {
                for (int c = this.col + 1; c < col; c++) {
                    if (board.getPiece(c, this.row) != null) {
                        return true;
                    }
                }
            }

            // up
            if (this.row > row) {
                for (int r = this.row - 1; r > row; r--) {
                    if (board.getPiece(this.col, r) != null) {
                        return true;
                    }
                }
            }

            // down
            if (this.row < row) {
                for (int r = this.row + 1; r < row; r++) {
                    if (board.getPiece(this.col, r) != null) {
                        return true;
                    }
                }
            }
        }
        else {

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
        }
        return false;
    }


}
