package main;

import GUI.AudioPlayer;
import pieces.Piece;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import ai.StockfishEngine;

public class Input extends MouseAdapter {

    Board board;
    private boolean isDragged = false;
    public int selectedX = -1, selectedY = -1;
    public boolean isStatusChanged = false, isCheckMate = false, isStaleMate = false, isWhiteTurn;
    public int col, row;
    AudioPlayer audioPlayer = new AudioPlayer();

    String pathToStockfish = "src/res/stockfish/stockfish-windows-x86-64.exe";
    StockfishEngine engine;

    public Input(Board board) {
        this.board = board;
        engine = new StockfishEngine();
        if (engine.startEngine(pathToStockfish)) {
            // System.out.println("Stockfish engine started.");
        } else {
           // System.out.println("Failed to start Stockfish engine.");
        }
    }

    private void makeEngineMove() {
        new Thread(() -> {
            boolean moveFound = false;
            while (!(moveFound) && !(board.getIsWhiteToMove()) ) {
                String fen = board.convertPiecesToFEN();
                // System.out.println("Current FEN: " + fen);
                String bestMove = engine.getBestMove(fen);
                // System.out.println("Best move: " + bestMove);

                if (bestMove != null && !bestMove.equals("unknown")) {
                    int fromCol = bestMove.charAt(0) - 'a';
                    int fromRow = 8 - (bestMove.charAt(1) - '0');
                    int toCol = bestMove.charAt(2) - 'a';
                    int toRow = 8 - (bestMove.charAt(3) - '0');
                    if (bestMove.length() > 4) {
                        engine.promotionChoice = String.valueOf(bestMove.charAt(4));
                    }
                    // System.out.println("From: " + fromCol + "," + fromRow + " To: " + toCol + "," + toRow);

                    Move move = new Move(board, board.getPiece(fromCol, fromRow), toCol, toRow);

                    if (board.isValidMove(move, true)) {
                        board.makeMove(move);
                        moveFound = true; // מהלך חוקי נמצא, לצאת מהלולאה
                        // System.out.println("Move found and made: " + bestMove);
                    } else {
                        // System.out.println("Move is invalid, retrying...");
                    }
                } else {
                    // System.out.println("No valid move found, retrying...");
                }
            }
            SwingUtilities.invokeLater(() -> {
                board.repaint();
                if (isStatusChanged) {
                    board.selectedPiece = null;
                    JFrame frame = new JFrame("Game Over");
                    board.updateGameState(true);
                    Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח") : "תיקו"));
                }
            });
        }).start();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (selectedX == -1 && selectedY == -1) {
            col = e.getX() / Board.tileSize;
            row = e.getY() / Board.tileSize;
            Piece pieceXY = board.getPiece(col, row);
            if (pieceXY != null && pieceXY.isWhite == board.getIsWhiteToMove() /* && pieceXY.isWhite */) { // ככה אי אפשר לשחק עבור השחור. נועד למשחק נגד המחשב
                audioPlayer.playSelectPieceSound();
                board.selectedPiece = pieceXY;
                selectedX = e.getX() - Board.tileSize / 2;
                selectedY = e.getY() - Board.tileSize / 2;
            } else {
                selectedX = -1;
                selectedY = -1;
            }
            board.repaint();
        } else {
            int col = e.getX() / Board.tileSize;
            int row = e.getY() / Board.tileSize;
            if (board.selectedPiece != null) {
                Move move = new Move(board, board.selectedPiece, col, row);
                if (board.isValidMove(move, true)) {
                    board.makeMove(move);
                    selectedX = -1;
                    selectedY = -1;
                    board.selectedPiece = null;
                    board.repaint();
                    if (isStatusChanged) {
                        board.selectedPiece = null;
                        board.repaint();
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "אין חומר מספיק. המשחק נגמר בתיקו.")));
                        });
                    } else {
//                        if (!board.getIsWhiteToMove()) {
//                            makeEngineMove();
//                        }
                    }
                } else {
                    board.selectedPiece.xPos = board.selectedPiece.col * Board.tileSize;
                    board.selectedPiece.yPos = board.selectedPiece.row * Board.tileSize;
                    col = e.getX() / Board.tileSize;
                    row = e.getY() / Board.tileSize;
                    Piece pieceXY = board.getPiece(col, row);
                    if (pieceXY != null && pieceXY.isWhite == board.getIsWhiteToMove()) {
                        audioPlayer.playSelectPieceSound();
                        board.selectedPiece = pieceXY;
                        selectedX = e.getX() - Board.tileSize / 2;
                        selectedY = e.getY() - Board.tileSize / 2;
                    } else {
                        audioPlayer.playInvalidMoveSound();
                        selectedX = -1;
                        selectedY = -1;
                        board.selectedPiece = null;
                        board.repaint();
                    }
                    board.repaint();
                }
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (board.selectedPiece != null) {
            board.selectedPiece.xPos = e.getX() - Board.tileSize / 2;
            board.selectedPiece.yPos = e.getY() - Board.tileSize / 2;
            board.repaint();
            isDragged = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (isDragged) {
            if (Math.abs(selectedX - board.selectedPiece.xPos) > Board.tileSize / 2 || Math.abs(selectedY - board.selectedPiece.yPos) > Board.tileSize / 2) {
                int col = e.getX() / Board.tileSize;
                int row = e.getY() / Board.tileSize;
                Move move = new Move(board, board.selectedPiece, col, row);
                if (board.isValidMove(move, true)) {
                    board.makeMove(move);
                    if (isStatusChanged) {
                        board.selectedPiece = null;
                        board.repaint();
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "אין חומר מספיק. המשחק נגמר בתיקו.")));
                        });
                    } else {
                        board.repaint();
//                        if (!board.getIsWhiteToMove()) {
//                            makeEngineMove();
//                        }
                    }
                } else {
                    board.selectedPiece.xPos = board.selectedPiece.col * Board.tileSize;
                    board.selectedPiece.yPos = board.selectedPiece.row * Board.tileSize;
                    audioPlayer.playInvalidMoveSound();
                }
                board.selectedPiece = null;
                board.repaint();
                selectedX = -1;
                selectedY = -1;
            } else {
                board.selectedPiece.xPos = board.selectedPiece.col * Board.tileSize;
                board.selectedPiece.yPos = board.selectedPiece.row * Board.tileSize;
                board.repaint();
            }
        }
        isDragged = false;
    }
}
