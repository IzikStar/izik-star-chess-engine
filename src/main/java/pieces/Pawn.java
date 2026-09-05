package pieces;

import ai.BoardState;
import main.Board;
import main.Move;
import main.setting.ChoosePlayFormat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class Pawn extends Piece{

    public int id;

    public Pawn(BoardState board, int col, int row, boolean isWhite, int id) {
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
        this.name = "Pawn";
        this.type = isWhite ? 'P' : 'p';
        this.id = id;
        this.value = 1;
        this.sprite = sheet.getSubimage(5 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        int colorIndex = isWhite ? 1 : -1;

        // push pawn 1:
        if (this.col == col && this.row == row + colorIndex && board.getPiece(col, row) == null)
            return true;

        // push pawn 2:
        if (this.col == col && this.row == row + 2 * colorIndex && this.row == (isWhite ? 6 : 1) && board.getPiece(col, row) == null && board.getPiece(col, row + colorIndex) == null)
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

    @Override
    public ArrayList<Move> getValidMoves(BoardState board) {
        ArrayList<Move> validMoves = new ArrayList<>();
        int colorIndex = isWhite ? -1 : 1;
        int promotionRow = isWhite ? 0 : 7;
        for (int i = col - 1; i <= col + 1; i++) {
            //System.out.println("c: " + c + " r: " + r);
            if (row + colorIndex >= 0 && row + colorIndex <= 7 && i >= 0 && i <= 7) {
                Move move = new Move(board, this, i, row + colorIndex);
                if (board.isValidMove(move)) {
                    if (row + colorIndex != promotionRow) {
                        validMoves.add(move);
                    }
                    else {
                        validMoves.add(move);
                    }
                }
            }
        }
        return validMoves;
    }

}
