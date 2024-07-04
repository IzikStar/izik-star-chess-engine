package pieces;

import ai.BoardState;
import main.Board;
import main.setting.ChoosePlayFormat;

import java.awt.image.BufferedImage;

public class Knight extends Piece{

    public Knight(BoardState board, int col, int row, boolean isWhite) {
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
        this.name = "Knight";
        this.type = isWhite ? 'N' : 'n';
        this.value = 3;

        this.sprite = sheet.getSubimage(3 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        return Math.abs(col - this.col) * Math.abs(row - this.row) == 2;
    }


}
