package main;

import GUI.AudioPlayer;
import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import main.setting.ChoosePlayFormat;
import main.setting.SettingPanel;
import pieces.Piece;
import rules.ChessMove;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Input extends MouseAdapter {

    Board board;
    private boolean isDragged = false;
    public int selectedX = -1, selectedY = -1;
    public boolean isStatusChanged = false, isCheckMate = false, isStaleMate = false, isRepetition = false, isWhiteTurn;
    public int col, row;
    AudioPlayer audioPlayer = new AudioPlayer();
    CountDownLatch latch = new CountDownLatch(1);

    /** Engines only pick moves; this class applies them on the Swing thread (Phase 3). */
    private final EngineSelector engines = new EngineSelector(new MinimaxEngine(), new StockfishEngine());
    /** One engine job at a time: Stockfish is a single process and the search is not reentrant. */
    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "engine");
        t.setDaemon(true);
        return t;
    });
    private Future<?> pendingEngineJob;
    /** Pause before a level-0 (random) move, so it doesn't appear instantly. */
    private static final long RANDOM_MOVE_DELAY_MS = 1000;
    /** The promotion piece of the engine move being applied; read by {@link Board#movePawn}. */
    String enginePromotion = "q";

    public boolean isDraggingMove = false;

    public Input(Board board) {
        this.board = board;
        if (!ChoosePlayFormat.isPlayingWhite) {
            makeEngineMove();
        }
    }

    public Input(Board board, CountDownLatch latch) {
        this(board);
    }

    /** Lets the engine choose a move for the side to move, then plays it on the Swing thread. */
    public void makeEngineMove() {
        latch = new CountDownLatch(1);
        CountDownLatch done = latch;
        String fen = board.state.toRulesFen();
        int level = SettingPanel.skillLevel;
        pendingEngineJob = engineExecutor.submit(() -> {
            try {
                if (level == 0) {
                    Thread.sleep(RANDOM_MOVE_DELAY_MS);
                }
                ChessMove move = engines.move(fen, level);
                if (move == null) {
                    System.err.println("Engine found no move in " + fen);
                } else {
                    SwingUtilities.invokeAndWait(() -> playEngineMove(fen, move));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // never swallow: this is how "the engine stops playing" used to hide
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
    }

    /** Shows the engine's suggestion for the side to move. */
    public void takeEngineHint() {
        String fen = board.state.toRulesFen();
        engineExecutor.submit(() -> {
            try {
                ChessMove move = engines.hint(fen);
                if (move != null) {
                    SwingUtilities.invokeLater(() -> showHint(fen, move));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /** Cancels a pending engine move or hint (go back / new game). */
    public void cancelEngine() {
        if (pendingEngineJob != null) {
            pendingEngineJob.cancel(true);
        }
    }

    public void shutdown() {
        cancelEngine();
        engineExecutor.shutdownNow();
        engines.close();
    }

    private void playEngineMove(String fen, ChessMove chosen) {
        if (!fen.equals(board.state.toRulesFen())) {
            return; // the position changed while the engine was thinking (go back / new game)
        }
        Piece piece = board.state.getPiece(chosen.from() % 8, chosen.from() / 8);
        Move move = new Move(board.state, piece, chosen.to() % 8, chosen.to() / 8);
        if (!board.state.isValidMove(move)) {
            System.err.println("Engine move " + chosen.toUci() + " rejected in " + fen);
            return;
        }
        enginePromotion = chosen.isPromotion() ? String.valueOf(chosen.promotion()) : "q";
        board.makeMove(move);
        board.repaint();
        if (isStatusChanged) {
            Board.selectedPiece = null;
            SwingUtilities.invokeLater(this::showGameOver);
        }
    }

    private void showHint(String fen, ChessMove move) {
        if (!fen.equals(board.state.toRulesFen())) {
            return;
        }
        board.hintFromC = move.from() % 8;
        board.hintFromR = move.from() / 8;
        board.hintToC = move.to() % 8;
        board.hintToR = move.to() / 8;
        audioPlayer.playHintSound();
        board.repaint();
    }

    private void showGameOver() {
        JFrame frame = new JFrame("Game Over");
        board.updateGameState(true);
        Main.showEndGameMessage(frame, (isCheckMate ? (isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "המשחק נגמר בתיקו.")));
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
                        SwingUtilities.invokeLater(this::showGameOver);
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
                        SwingUtilities.invokeLater(this::showGameOver);
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
