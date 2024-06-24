package pieces;

import main.Board;
import main.Move;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

public class Piece {

    public int col, row;
    public int xPos, yPos;

    public boolean isWhite;
    public String name;
    public char type;
    public int value;
    public boolean isFirstMove = true;

    BufferedImage sheet;
    {
        try {
            sheet = ImageIO.read(ClassLoader.getSystemResourceAsStream("pieces.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    protected int sheetScale = sheet.getWidth()/6;

    Image sprite;

    Board board;

    public Piece(Board board) {
        this.board = board;
    }

    public void paint(Graphics2D g2d) {
        g2d.drawImage(sprite, xPos, yPos, null);
    }

    public boolean isValidMovement(int col, int row) {return true;}

    public boolean moveCollidesWithPiece(int col, int row) {return false;}

    public ArrayList<Move> getValidMoves(Board board) {
        ArrayList<Move> validMoves = new ArrayList<>();
        for (int c = 0; c < 8; c++) {
            for (int r = 0; r < 8; r++) {
                if (board.isValidMove(new Move(board, this, c, r ))) {
                    if (board.getPiece(c, r) == null) {
                        validMoves.add(new Move(board, this ,c ,r ));
                    }
                }
            }
        }
        return validMoves;
    }

    public char getRepresentation() {
        return isWhite ? Character.toUpperCase(type) : Character.toLowerCase(type);
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }
}
