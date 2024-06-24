package main;

import pieces.Piece;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import ai.StockfishEngine;

public class Input extends MouseAdapter {

    Board board;
    private boolean isDragged = false;
    public int selectedX = -1, selectedY = -1;
    public boolean isStatusChanged = false, isCheckMate = false, isStaleMate = false, isWhiteTurn;
    public int col, row;

    // הפעלת Stockfish והפעלת המשחק
    String pathToStockfish = "src/res/stockfish/stockfish-windows-x86-64.exe";
    StockfishEngine engine;

    public Input(Board board) {
        this.board = board;
//        engine = new StockfishEngine();
//        if (engine.startEngine(pathToStockfish)) {
//            System.out.println("Stockfish engine started.");
//        } else {
//            System.out.println("Failed to start Stockfish engine.");
//        }
    }

    private void makeEngineMove() {
        // שימוש במתודה להמרת רשימת הכלים ל-FEN
        String fen = board.convertPiecesToFEN();
        System.out.println("Current FEN: " + fen);
        String bestMove = engine.getBestMove(fen);
        System.out.println("Best move: " + bestMove);
        if (!bestMove.equals("unknown")) {
            // Translate the best move to board coordinates and make the move
            int fromCol = bestMove.charAt(0) - 'a';
            int fromRow = 8 - (bestMove.charAt(1) - '0');
            int toCol = bestMove.charAt(2) - 'a';
            int toRow = 8 - (bestMove.charAt(3) - '0');
            Move move = new Move(board, board.getPiece(fromCol, fromRow), toCol, toRow);
            board.makeMove(move);
            board.repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (selectedX == -1 && selectedY == -1) {
            col = e.getX() / Board.tileSize;
            row = e.getY() / Board.tileSize;
            Piece pieceXY = board.getPiece(col, row);
            if (pieceXY != null && pieceXY.isWhite == board.getIsWhiteToMove()) {
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
                if (board.isValidMove(move)) {
                    board.makeMove(move);
                    selectedX = -1;
                    selectedY = -1;
                    board.selectedPiece = null;
                    board.repaint();
                    if (isStatusChanged) {
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "אין חומר מספיק. המשחק נגמר בתיקו.")));
                        });
//                    } else {
//                        // Make the engine move
//                        // makeEngineMove();
                    }
                } else {
                    board.selectedPiece.xPos = board.selectedPiece.col * Board.tileSize;
                    board.selectedPiece.yPos = board.selectedPiece.row * Board.tileSize;
                    col = e.getX() / Board.tileSize;
                    row = e.getY() / Board.tileSize;
                    Piece pieceXY = board.getPiece(col, row);
                    if (pieceXY != null && pieceXY.isWhite == board.getIsWhiteToMove()) {
                        board.selectedPiece = pieceXY;
                        selectedX = e.getX() - Board.tileSize / 2;
                        selectedY = e.getY() - Board.tileSize / 2;
                    } else {
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
                if (board.isValidMove(move)) {
                    board.makeMove(move);
                    if (isStatusChanged) {
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "אין חומר מספיק. המשחק נגמר בתיקו.")));
                        });
//                    } else {
//                        // Make the engine move
//                        // makeEngineMove();
                    }
                } else {
                    board.selectedPiece.xPos = board.selectedPiece.col * Board.tileSize;
                    board.selectedPiece.yPos = board.selectedPiece.row * Board.tileSize;
                }
                board.selectedPiece = null;
                board.repaint();
                selectedX = -1;
                selectedY = -1;
            }
            else {
                board.selectedPiece.xPos = board.selectedPiece.col * Board.tileSize;
                board.selectedPiece.yPos = board.selectedPiece.row * Board.tileSize;
                board.repaint();
            }
        }
        isDragged = false;
    }

}
