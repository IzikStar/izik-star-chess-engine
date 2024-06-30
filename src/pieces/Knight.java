package pieces;

import main.Board;
import main.setting.ChoosePlayFormat;

import java.awt.image.BufferedImage;

public class Knight extends Piece{

    public Knight(Board board, int col, int row, boolean isWhite) {
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
        this.name = "Knight";
        this.type = isWhite ? 'N' : 'n';
        this.value = 3;

        this.sprite = sheet.getSubimage(3 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(board.tileSize, board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        return Math.abs(col - this.col) * Math.abs(row - this.row) == 2;
    }


}
