package main;

import GUI.AudioPlayer;
import ai.myEngine;
import main.setting.ChoosePlayFormat;
import main.setting.SettingPanel;
import pieces.Piece;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import ai.StockfishEngine;

public class Input extends MouseAdapter {

    Board board;
    private boolean isDragged = false;
    public int selectedX = -1, selectedY = -1;
    public boolean isStatusChanged = false, isCheckMate = false, isStaleMate = false, isRepetition = false, isWhiteTurn;
    public int col, row;
    AudioPlayer audioPlayer = new AudioPlayer();
    CountDownLatch latch = new CountDownLatch(1);

    String pathToStockfish = "src/res/stockfish/stockfish-windows-x86-64.exe";
    StockfishEngine engine;
    myEngine myEngine;

    public boolean isDraggingMove = false;
    public int switchToStockFish = 13;

    public Input(Board board) {
        this.board = board;
        engine = new StockfishEngine();
        myEngine = new myEngine(board.state);
        if (!ChoosePlayFormat.isPlayingWhite) {
            makeEngineMove();
        }
    }

    public Input(Board board, CountDownLatch latch) {
        this.board = board;
        engine = new StockfishEngine();
        myEngine = new myEngine(board.state, latch);
        if (!ChoosePlayFormat.isPlayingWhite) {
            makeEngineMove();
        }
    }

    public void makeEngineMove() {
        if (SettingPanel.skillLevel < switchToStockFish) {
            latch = new CountDownLatch(1);
            Future<Void> future = myEngine.makeMove(board.state.convertPiecesToFEN(), board);
            new Thread(() -> {
                try {
                    future.get(); // Wait for the task to complete
                } catch (Exception e) {
                    // Handle exceptions
                } finally {
                    latch.countDown(); // Release the latch
                }
            }).start();
        }
        else {
            new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    engine.setSkillLevel(SettingPanel.skillLevel - 1);
                    boolean moveFound = false;
                    long endTime = System.currentTimeMillis();
                    while (!(moveFound) && endTime - startTime < 1200) {
                        engine.setSkillLevel(ChoosePlayFormat.setSkillLevel);
                        String fen = board.state.convertPiecesToFEN();
                        String bestMove = engine.getBestMove(fen);

                        if (bestMove != null && !bestMove.equals("unknown")) {
                            int fromCol = bestMove.charAt(0) - 'a';
                            int fromRow = 8 - (bestMove.charAt(1) - '0');
                            int toCol = bestMove.charAt(2) - 'a';
                            int toRow = 8 - (bestMove.charAt(3) - '0');
                            if (bestMove.length() > 4) {
                                engine.promotionChoice = String.valueOf(bestMove.charAt(4));
                            } else {
                                engine.promotionChoice = null;
                            }

                            Move move = new Move(board.state, board.state.getPiece(fromCol, fromRow), toCol, toRow);

                            if (board.state.isValidMove(move)) {
                                board.makeMove(move);
                                moveFound = true; // Move found, exit the loop
                            }
                        }
                        endTime = System.currentTimeMillis();
                    }

                    if (!moveFound) {
                        int temp = SettingPanel.skillLevel;
                        SettingPanel.skillLevel = 3;
                        ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
                        myEngine.makeMove(board.state.convertPiecesToFEN(), board);
                        ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
                        SettingPanel.skillLevel = temp;
                    }

                    SwingUtilities.invokeLater(() -> {
                        board.repaint();
                        if (isStatusChanged) {
                            Board.selectedPiece = null;
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח") : "תיקו"));
                        }
                    });

                } finally {
                    latch.countDown(); // Release the latch when done
                }
            }).start();
        }
    }

    public void takeEngineHint() {
        boolean takeMyEngineHints = false;
        if (takeMyEngineHints) {
            int temp = SettingPanel.skillLevel;
            SettingPanel.skillLevel = 12;
            ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
            myEngine.giveHint(board.state.convertPiecesToFEN(), board);
            ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
            SettingPanel.skillLevel = temp;
        }
        else {
            new Thread(() -> {
                long startTime = System.currentTimeMillis();
                boolean moveFound = false;
                long endTime = System.currentTimeMillis();
                while ((!moveFound) && endTime - startTime < 1500) {
                    engine.setSkillLevel(20);
                    String fen = board.state.convertPiecesToFEN();
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
                        } else {
                            engine.promotionChoice = null;
                        }
                        // System.out.println("From: " + fromCol + "," + fromRow + " To: " + toCol + "," + toRow);

                        Move move = new Move(board.state, board.state.getPiece(fromCol, fromRow), toCol, toRow);

                        if (board.state.isValidMove(move)) {
                            board.hintFromC = fromCol;
                            board.hintFromR = fromRow;
                            board.hintToC = toCol;
                            board.hintToR = toRow;
                            audioPlayer.playHintSound();
                            board.repaint();
                            engine.setSkillLevel(SettingPanel.skillLevel);
                            moveFound = true; // מהלך חוקי נמצא, לצאת מהלולאה
                            // System.out.println("Move found and made: " + bestMove);
                        }
                    } else {
                        // System.out.println("No valid move found, retrying...");
                        endTime = System.currentTimeMillis();
                    }
                }
                // System.out.println(endTime - startTime);
                if (!moveFound) {
                    System.out.println("taking to long");
                    int temp = SettingPanel.skillLevel;
                    SettingPanel.skillLevel = 10;
                    ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
                    myEngine.giveHint(board.state.convertPiecesToFEN(), board);
                    ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
                    SettingPanel.skillLevel = temp;
                }
                SwingUtilities.invokeLater(() -> {
                    board.repaint();
                    if (isStatusChanged) {
                        Board.selectedPiece = null;
                        JFrame frame = new JFrame("Game Over");
                        board.updateGameState(true);
                        Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח") : "תיקו"));
                    }
                });
            }).start();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // deleting the hint mark
        board.hintFromC = -1;
        board.hintFromR = -1;
        board.hintToC = -1;
        board.hintToR = -1;
        if (selectedX == -1 && selectedY == -1) {
            // בחירת כלי
            col = board.getColFromX(e.getX());
            row = board.getRowFromY(e.getY());

            Piece pieceXY = board.state.getPiece(col, row);
            if (pieceXY != null && pieceXY.isWhite == board.state.getIsWhiteToMove() && (!ChoosePlayFormat.isOnePlayer || ChoosePlayFormat.isPlayingWhite == board.state.getIsWhiteToMove())) {
                audioPlayer.playSelectPieceSound();
                Board.selectedPiece = pieceXY;
                selectedX = board.getColFromX(e.getX());
                selectedY = board.getRowFromY(e.getY());
            } else {
                selectedX = -1;
                selectedY = -1;
            }
            board.repaint();
        } else {
            // הזזת כלי
            col = board.getColFromX(e.getX());
            row = board.getRowFromY(e.getY());

            if (Board.selectedPiece != null) {
                Move move = new Move(board.state, Board.selectedPiece, col, row);
                if (board.state.isValidMove(move)) {
                    board.makeMove(move);
                    selectedX = -1;
                    selectedY = -1;
                    Board.selectedPiece = null;
                    board.repaint();
                    if (isStatusChanged) {
                        Board.selectedPiece = null;
                        board.repaint();
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "המשחק נגמר בתיקו.")));
                        });
                    } else {
                        if ((ChoosePlayFormat.isOnePlayer && ChoosePlayFormat.isPlayingWhite != board.state.getIsWhiteToMove())) {
                            makeEngineMove();
                        }
                        if (!ChoosePlayFormat.isOnePlayer) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(500);
                                    audioPlayer.playSwitchSound();
                                    Thread.sleep(500);
                                } catch (InterruptedException event) {
                                    event.printStackTrace();
                                }
                                ChoosePlayFormat.isPlayingWhite = board.state.getIsWhiteToMove();
                                board.loadPiecesFromFen(board.state.fenCurrentPosition);
                            }).start();
                        }
                    }
                } else {
                    Board.selectedPiece.xPos = board.getXFromCol(Board.selectedPiece.col);
                    Board.selectedPiece.yPos = board.getYFromRow(Board.selectedPiece.row);
                    col = board.getColFromX(e.getX());
                    row = board.getRowFromY(e.getY());

                    Piece pieceXY = board.state.getPiece(col, row);
                    if (pieceXY != null && pieceXY.isWhite == board.state.getIsWhiteToMove()) {
                        audioPlayer.playSelectPieceSound();
                        Board.selectedPiece = pieceXY;
                        selectedX = board.getColFromX(e.getX());
                        selectedY = board.getRowFromY(e.getY());
                    } else {
                        audioPlayer.playInvalidMoveSound();
                        selectedX = -1;
                        selectedY = -1;
                        Board.selectedPiece = null;
                        board.repaint();
                    }
                    board.repaint();
                }
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (Board.selectedPiece != null) {
            Board.selectedPiece.xPos = e.getX() -Board.tileSize / 2;
            Board.selectedPiece.yPos = e.getY() - Board.tileSize / 2;
            board.repaint();
            isDragged = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (isDragged) {
            if (Math.abs(selectedX - Board.selectedPiece.xPos) > Board.tileSize / 2 || Math.abs(selectedY - Board.selectedPiece.yPos) > Board.tileSize / 2) {
                col = board.getColFromX(e.getX());
                row = board.getRowFromY(e.getY());
                Move move = new Move(board.state, Board.selectedPiece, col, row);
                if (board.state.isValidMove(move)) {
                    isDraggingMove = true;
                    board.makeMove(move);
                    isDraggingMove = false;
                    if (isStatusChanged) {
                        Board.selectedPiece = null;
                        board.repaint();
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            board.updateGameState(true);
                            Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "המשחק נגמר בתיקו.")));
                        });
                    } else {
                        board.repaint();
                        if (ChoosePlayFormat.isOnePlayer && ChoosePlayFormat.isPlayingWhite != board.state.getIsWhiteToMove()) {
                            makeEngineMove();
                        }
                        if (!ChoosePlayFormat.isOnePlayer) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(500);
                                    audioPlayer.playSwitchSound();
                                    Thread.sleep(500);
                                } catch (InterruptedException event) {
                                    event.printStackTrace();
                                }
                                ChoosePlayFormat.isPlayingWhite = board.state.getIsWhiteToMove();
                                Board.selectedPiece = null;
                                board.loadPiecesFromFen(board.state.fenCurrentPosition);
                                selectedX = -1;
                                selectedY = -1;
                            }).start();
                        }
                    }
                }
                else {
                    Board.selectedPiece.xPos = board.getXFromCol(Board.selectedPiece.col);
                    Board.selectedPiece.yPos = board.getYFromRow(Board.selectedPiece.row);
                    audioPlayer.playInvalidMoveSound();
                }
                Board.selectedPiece = null;
                board.repaint();
                selectedX = -1;
                selectedY = -1;
            }
            else {
                Board.selectedPiece.xPos = board.getXFromCol(Board.selectedPiece.col);
                Board.selectedPiece.yPos = board.getYFromRow(Board.selectedPiece.row);
                board.repaint();
            }
        }
        isDragged = false;
    }

}
