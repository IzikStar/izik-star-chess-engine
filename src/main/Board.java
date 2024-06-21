package main;

import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class Board extends JPanel {

    public int tileSize = 85;

    int cols = 8;
    int rows = 8;

    public Piece selectedPiece;
    Input input = new Input(this);

    public int enPassantTile = -1;

    public Board() {
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));

        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        addPieces();
    }

    ArrayList<Piece> pieceList = new ArrayList<>();

    public Piece getPiece(int col, int row) {

        for (Piece piece : pieceList) {
            if (piece.col == col && piece.row == row) {
                return piece;
            }
        }


        return null;
    }

    public void addPieces() {
        pieceList.add(new Knight(this, 1,0, false));
        pieceList.add(new Knight(this, 6,0, false));
        pieceList.add(new Knight(this, 1,7, true));
        pieceList.add(new Knight(this, 6,7, true));

        pieceList.add(new Rook(this, 0,0, false));
        pieceList.add(new Rook(this, 7,0, false));
        pieceList.add(new Rook(this, 0,7, true));
        pieceList.add(new Rook(this, 7,7, true));

        pieceList.add(new Bishop(this, 2,0, false));
        pieceList.add(new Bishop(this, 5,0, false));
        pieceList.add(new Bishop(this, 2,7, true));
        pieceList.add(new Bishop(this, 5,7, true));

        pieceList.add(new King(this, 4,0, false));
        pieceList.add(new King(this, 4,7, true));

        pieceList.add(new Queen(this, 3,0, false));
        pieceList.add(new Queen(this, 3,7, true));

        for (int i = 0; i < 8; i++) {
            pieceList.add(new Pawn(this, i, 1, false));
            pieceList.add(new Pawn(this, i, 6, true));
        }
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        // paint board
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 == 0 ? new Color(152, 97, 42) : new Color(208, 182, 164));
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
            }
        }

        // paint highLights
        if (selectedPiece != null) {
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (isValidMove(new Move(this, selectedPiece, c, r))) {
                        g2d.setColor(new Color(85, 241, 85, 215));
                        g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                    }
                }
            }
        }

        // paint pieces
        for (Piece piece : pieceList) {
            piece.paint(g2d);
        }
    }

    public void makeMove(Move move) {

        if (move.piece.name.equals("Pawn")) {
            movePawn(move);
        }
        else {
            move.piece.col = move.newCol;
            move.piece.row = move.newRow;
            move.piece.xPos = move.newCol * tileSize;
            move.piece.yPos = move.newRow * tileSize;

            move.piece.isFirstMove = false;
            capture(move.captured);
        }

    }

    public void capture(Piece piece) {
        pieceList.remove(piece);
    }

    public boolean isValidMove (Move move) {

        if (sameTeam(move.piece, move.captured)) {
            return false;
        }
        if (!move.piece.isValidMovement(move.newCol, move.newRow)) {
            return false;
        }
        if (move.piece.moveCollidesWithPiece(move.newCol, move.newRow)) {
            return false;
        }

        return true;
    }

    public boolean sameTeam(Piece p1, Piece p2){
        if (p1 == null || p2 == null) {
            return false;
        }
        return p1.isWhite == p2.isWhite;
    }

    public int getTileNum(int col, int row) {
        return rows * row + col;
    }

    public void movePawn(Move move) {

        // en passant:
        int colorIndex = move.piece.isWhite ? 1 : -1;

        if (getTileNum(move.newCol, move.newRow) == enPassantTile) {
            move.captured = getPiece(move.newCol, move.newRow + colorIndex);
        }
        if (Math.abs(move.piece.row - move.newRow) == 2) {
            enPassantTile = getTileNum(move.newCol, move.newRow + colorIndex);
        } else {
            enPassantTile = -1;
        }

        // promotions:
        colorIndex = move.piece.isWhite ? 0 : 7;
        if (move.newRow == colorIndex) {
            promotePawn(move);
            capture(move.piece);
        }



        move.piece.col = move.newCol;
        move.piece.row = move.newRow;
        move.piece.xPos = move.newCol * tileSize;
        move.piece.yPos = move.newRow * tileSize;

        move.piece.isFirstMove = false;
        capture(move.captured);
    }

    private void promotePawn(Move move) {
        pieceList.add(new Queen(this, move.newCol, move.newRow, move.piece.isWhite));
        capture(move.piece);
    }


}
