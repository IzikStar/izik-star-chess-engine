package main;

import GUI.AudioPlayer;
import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.stream.Collectors;
import ai.*;

public class Board extends JPanel {

    public static int tileSize = 85;

    private JFrame parentFrame;

    int cols = 8;
    int rows = 8;

    public Piece selectedPiece;
    Input input = new Input(this);
    public CheckScanner checkScanner = new CheckScanner(this);
    AudioPlayer audioPlayer = new AudioPlayer();

    public int enPassantTile = -1;

    private boolean isWhiteToMove = true;
    public boolean isGameOver = false;
    public static boolean isStatusChanged = false, isCheckMate = false, isStaleMate = false, isWhiteTurn;

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

    Piece findKing(boolean isWhite) {
        for (Piece piece : pieceList) {
            if (piece.isWhite == isWhite && piece.name.equals("King")) {
                return piece;
            }
        }
        return null;
    }

    public void addPieces() {
        pieceList.add(new Rook(this, 0, 0, false));
        pieceList.add(new Rook(this, 7, 0, false));
        pieceList.add(new Rook(this, 0, 7, true));
        pieceList.add(new Rook(this, 7, 7, true));

        pieceList.add(new Knight(this, 1, 0, false));
        pieceList.add(new Knight(this, 6, 0, false));
        pieceList.add(new Knight(this, 1, 7, true));
        pieceList.add(new Knight(this, 6, 7, true));

        pieceList.add(new Bishop(this, 2, 0, false));
        pieceList.add(new Bishop(this, 5, 0, false));
        pieceList.add(new Bishop(this, 2, 7, true));
        pieceList.add(new Bishop(this, 5, 7, true));

        pieceList.add(new King(this, 4, 0, false));
        pieceList.add(new King(this, 4, 7, true));

        pieceList.add(new Queen(this, 3, 0, false));
        pieceList.add(new Queen(this, 3, 7, true));

        for (int i = 0; i < 8; i++) {
            pieceList.add(new Pawn(this, i, 1, false, 1));
            pieceList.add(new Pawn(this, i, 6, true, 1));
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
            if (selectedPiece.isWhite == isWhiteToMove) {
                g2d.setColor(new Color(0, 0, 255, 128)); // כחול חצי שקוף
                g2d.fillRect(selectedPiece.col * tileSize, selectedPiece.row * tileSize, tileSize, tileSize);
            }
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (isValidMove(new Move(this, selectedPiece, c, r), false)) {
                        if (this.getPiece(c, r) == null) {
                            g2d.setColor(new Color(64, 227, 64, 215));
                            g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                        } else {
                            g2d.setColor(new Color(238, 7, 7, 111)); // אדום חצי שקוף
                            g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                        }
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
        boolean pawnMoveSuccess = true;
        if (move.piece.name.equals("Pawn")) {
            pawnMoveSuccess = movePawn(move);
        } else if (move.piece.name.equals("King")) {
            moveKing(move);
        }

        if (pawnMoveSuccess) {
            if (!move.piece.name.equals("Pawn") || !(Math.abs(move.piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            move.piece.col = move.newCol;
            move.piece.row = move.newRow;
            move.piece.xPos = move.newCol * tileSize;
            move.piece.yPos = move.newRow * tileSize;
            move.piece.isFirstMove = false;
            audioPlayer.playMovingPieceSound();
            if (move.captured != null) {
                audioPlayer.playCaptureSound();
            }
            capture(move.captured);
            isWhiteToMove = !isWhiteToMove;
            updateGameState(true);
        }
    }

    public void capture(Piece piece) {
        pieceList.remove(piece);
    }

    public boolean isValidMove(Move move, boolean isExecuting) {

        if (isGameOver) {
            return false;
        }
        if (move.piece.isWhite != isWhiteToMove) {
            return false;
        }
        if (sameTeam(move.piece, move.captured)) {
            return false;
        }
        if (!move.piece.isValidMovement(move.newCol, move.newRow)) {
            return false;
        }
        if (move.piece.moveCollidesWithPiece(move.newCol, move.newRow)) {
            return false;
        }
        if (checkScanner.isKingChecked(move)) {
            if (isExecuting) {
                audioPlayer.playInvalidMoveBecauseOfCheckSound();
            }
            return false;
        }

        return true;
    }

    public boolean sameTeam(Piece p1, Piece p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return p1.isWhite == p2.isWhite;
    }

    public int getTileNum(int col, int row) {
        return rows * row + col;
    }

    public void moveKing(Move move) {
        if (Math.abs(move.piece.col - move.newCol) == 2) {
            Piece rook;
            if (move.piece.col < move.newCol) {
                rook = getPiece(7, move.piece.row);
                rook.col = 5;
            } else {
                rook = getPiece(0, move.piece.row);
                rook.col = 3;
            }
            rook.xPos = rook.col * tileSize;
        }
    }

    public boolean movePawn(Move move) {

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
            return promotePawn(move);
        }
        return true;
    }

    private boolean promotePawn(Move move) {
        PromotionDialog promotionDialog = new PromotionDialog(parentFrame, move.piece.isWhite);
        String choice = promotionDialog.getSelection();
        if (choice != null) {
            promotePawnTo(move, choice);
            capture(move.piece);
        } else {
            System.out.println("No selection made");
            // promotePawn(move);
            selectedPiece.xPos = selectedPiece.col * tileSize;
            selectedPiece.yPos = selectedPiece.row * tileSize;
            selectedPiece = null;
            repaint();
            input.selectedX = -1;
            input.selectedY = -1;
            return false;
        }
        return true;
    }

    private void promotePawnTo(Move move, String choice) {
        switch (choice) {
            case "Queen":
                pieceList.add(new Queen(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
            case "Rook":
                pieceList.add(new Rook(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
            case "Bishop":
                pieceList.add(new Bishop(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
            case "Knight":
                pieceList.add(new Knight(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
        }
    }

    public void updateGameState(boolean isRealBoard) {
        Piece king = findKing(isWhiteToMove);
        if (checkScanner.isKingChecked(new Move(this, king, king.col, king.row))) {
            audioPlayer.playCheckSound();
        }
        if (checkScanner.isGameOver(king)) {
            if (checkScanner.isKingChecked(new Move(this, king, king.col, king.row))) {
                System.out.println(isWhiteToMove ? "black wins!" : "white wins!");
                if (isRealBoard){
                    input.isStatusChanged = true;
                    input.isCheckMate = true;
                    input.isStaleMate = false;
                    input.isWhiteTurn = isWhiteToMove;
                }
            } else {
                System.out.println("stale mate! draw!");
                if (isRealBoard){
                    input.isStatusChanged = true;
                    input.isCheckMate = false;
                    input.isStaleMate = true;
                    input.isWhiteTurn = isWhiteToMove;
                }
            }
//            isGameOver = true;
        } else if (insufficientMaterial(true) && insufficientMaterial(false)) {
            System.out.println("insufficientMaterial! draw!");
//            isGameOver = true;
            if (isRealBoard){
                input.isStatusChanged = true;
                input.isCheckMate = false;
                input.isStaleMate = false;
                input.isWhiteTurn = isWhiteToMove;
            }
        }

    }

    private boolean insufficientMaterial(boolean isWhite) {
        ArrayList<String> names = pieceList.stream()
                .filter(p -> p.isWhite == isWhite)
                .map(p -> p.name)
                .collect(Collectors.toCollection(ArrayList::new));
        if (names.contains("Queen") || names.contains("Rook") || names.contains("Pawn")) {
            return false;
        }
        return names.size() < 3;
    }

    public boolean getIsWhiteToMove() {
        return this.isWhiteToMove;
    }

    public double getNumOfPieces() {
        return pieceList.size();
    }

    public Piece getPieceByNumber(int randomNum) {
        return pieceList.get(randomNum);
    }

    public String convertPiecesToFEN() {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            int emptySquares = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = getPiece(row, col);
                if (piece == null) {
                    emptySquares++;
                } else {
                    if (emptySquares > 0) {
                        fen.append(emptySquares);
                        emptySquares = 0;
                    }
                    fen.append(piece.getRepresentation());
                }
            }
            if (emptySquares > 0) {
                fen.append(emptySquares);
            }
            if (row < 7) {
                fen.append('/');
            }
        }
        fen.append(isWhiteTurn ? " w " : " b "); // תור השחקן הבא
        fen.append("KQkq - 0 1"); // מצב העתקה (castling), עובר דרך, חצאים, שלמים
        return fen.toString();
    }


}

