package pieces;

import ai.BoardState;
import main.Board;
import main.Move;
import main.setting.ChoosePlayFormat;

import java.awt.image.BufferedImage;

public class King extends Piece{
    public King(BoardState board, int col, int row, boolean isWhite) {
        super(board);
        this.col = col;
        this.row = row;
        if (ChoosePlayFormat.isPlayingWhite) {
            this.xPos = col * Board.tileSize;
            this.yPos = row * Board.tileSize;
        }
        else {
            this.xPos = (7 - col) * Board.tileSize;
            this.yPos = (7 - row) * Board.tileSize;
        }

        this.isWhite = isWhite;
        this.name = "King";
        this.type = isWhite ? 'K' : 'k';
        this.value = 2000000;

        this.sprite = sheet.getSubimage(0 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        return ((Math.abs(this.col - col) * Math.abs(this.row - row) == 1) || (Math.abs(this.col - col) + Math.abs(this.row - row) == 1)) || canCastle(col, row);
    }

    private boolean canCastle(int col, int row) {
        if (board.getIsWhiteToMove() ? this.row != 7 : this.row != 0) {
            return false;
        }
        if (this.row == row) {
            if (col == 6) {
                Piece rook = board.getPiece(7, row);
                if (rook != null && rook.isFirstMove && isFirstMove && rook.name.equals("Rook")) {
                    return  board.getPiece(5, row) == null &&
                            board.getPiece(6, row) == null &&
                            !board.checkScanner.isMoveCausesCheck(new Move(board,this, 5, row)) && !board.checkScanner.isChecking(board);
                }
            } else if (col == 2) {
                Piece rook = board.getPiece(0, row);
                if (rook != null && rook.isFirstMove && isFirstMove && rook.name.equals("Rook")) {
                    return  board.getPiece(3, row) == null &&
                            board.getPiece(2, row) == null &&
                            board.getPiece(1, row) == null &&
                            !board.checkScanner.isMoveCausesCheck(new Move(board,this, 3, row));
                }
            }
        }

        return false;
    }

}
