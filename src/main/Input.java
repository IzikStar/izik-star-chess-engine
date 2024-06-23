package main;

import pieces.Piece;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class Input extends MouseAdapter {

    Board board;
    private boolean isDragged = false;
    private int selectedX = -1, selectedY = -1;

    public Input(Board board) {
        this.board = board;
    }


    @Override
    public void mousePressed(MouseEvent e) {
        if (selectedX == -1 && selectedY == -1) {
            int col = e.getX() / board.tileSize;
            int row = e.getY() / board.tileSize;
            selectedX = col;
            selectedY = row;
            Piece pieceXY = board.getPiece(col, row);
            if (pieceXY != null) {
                board.selectedPiece = pieceXY;
                // System.out.println("נבחר כלי");
            }
            else {
                selectedX = -1;
                selectedY = -1;
            }
            board.repaint();
        }
        else {
            int col = e.getX() / board.tileSize;
            int row = e.getY() / board.tileSize;
            if (board.selectedPiece != null) {
                Move move = new Move(board, board.selectedPiece, col, row);

                if (board.isValidMove(move)) {
                    board.makeMove(move);
                    // System.out.println("מזיז את הכלי לנקודה שנבחרה");
                    selectedX = -1;
                    selectedY = -1;
                }
                else {
                    board.selectedPiece.xPos = board.selectedPiece.col * board.tileSize;
                    board.selectedPiece.yPos = board.selectedPiece.row * board.tileSize;
                }

                board.selectedPiece = null;
                board.repaint();
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (board.selectedPiece != null) {
            // System.out.println("גרירת עכבר.");
            board.selectedPiece.xPos = e.getX() - board.tileSize / 2;
            board.selectedPiece.yPos = e.getY() - board.tileSize / 2;

            board.repaint();
            isDragged = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (isDragged) {
            // System.out.println("מניח את הכלי בנקודה אליה הוא נגרר.");
            int col = e.getX() / board.tileSize;
            int row = e.getY() / board.tileSize;
            if (board.selectedPiece != null) {
                Move move = new Move(board, board.selectedPiece, col, row);

                if (board.isValidMove(move)) {
                    board.makeMove(move);
                }
                else {
                    board.selectedPiece.xPos = board.selectedPiece.col * board.tileSize;
                    board.selectedPiece.yPos = board.selectedPiece.row * board.tileSize;
                }
                board.selectedPiece = null;
                board.repaint();
            }
            selectedX = -1;
            selectedY = -1;
            isDragged = false;
        }
//        else {
//            System.out.println("לא עושה כלום. לא היתה גרירת עכבר.");
//        }
    }

//    private void handleSquareClick(int x, int y) {
//        Piece selectedPiece = board.getPiece(x, y);
//
//        if (selectedX == -1 && selectedY == -1) {
//            if (selectedPiece != null && selectedPiece.isWhite == boardState.isWhiteTurn) {
//                selectedX = x;
//                selectedY = y;
//                Point[] possibleMoves = selectedPiece.showAllPossibleMoves(boardState, true);
//                legalMoves.clear();
//                attackMoves.clear();
//                for (Point move : possibleMoves) {
//                    if (boardState.getPiece(move.x, move.y) != null) {
//                        attackMoves.add(move);
//                    } else {
//                        legalMoves.add(move);
//                    }
//                }
//            }
//        } else {
//            if (board.movePiece(selectedX, selectedY, x, y, true)) {
//                selectedX = -1;
//                selectedY = -1;
//                legalMoves.clear();
//                attackMoves.clear();
//            } else {
//                if (selectedPiece != null && selectedPiece.isBlack() != boardState.isWhiteTurn) {
//                    selectedX = x;
//                    selectedY = y;
//                    Point[] possibleMoves = selectedPiece.showAllPossibleMoves(boardState, true);
//                    legalMoves.clear();
//                    attackMoves.clear();
//                    for (Point move : possibleMoves) {
//                        if (boardState.getPiece(move.x, move.y) != null) {
//                            attackMoves.add(move);
//                        } else {
//                            legalMoves.add(move);
//                        }
//                    }
//                } else {
//                    selectedX = -1;
//                    selectedY = -1;
//                    legalMoves.clear();
//                    attackMoves.clear();
//                }
//            }
//        }
//        updateChessBoard();
//        if (isStatusChanged) {
//            // בדיקת מצב המשחק לאחר הצביעה מחדש
//            SwingUtilities.invokeLater(() -> boardState.checkGameState(isCheckMate, isStaleMate, isWhiteTurn));
//        }
//    }

}
