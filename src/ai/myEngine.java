package ai;

import ai.BitBoard.BitMove;
import ai.openingBook.OpeningBook;
import main.Board;
import main.Main;
import main.Move;
import main.setting.SettingPanel;
import pieces.Piece;

import javax.swing.*;
import java.util.ArrayList;
import java.util.concurrent.*;

public class myEngine {
    // board state:
    BoardState board;
    private String fen;

    // for the random engine:
    private ArrayList<Move> randomMovesList;
    public String promotionChoice;
    private final ArrayList<Piece> alreadyChecked = new ArrayList<>();

    // general parameters:
    public static int waitTime = 1000;
    public static Thread thread;
    private final CountDownLatch latch;

    // for making move methods:
    public Piece chosenPiece;
    private static final int DEPTH = 2;

    // ספר הפתיחות
    private static OpeningBook openingBook;

//    static {
//        try {
//            openingBook = OpeningBook.loadFromFile("opening_book.dat");
//        } catch (IOException | ClassNotFoundException e) {
//            openingBook = new OpeningBook();  // אם הקובץ לא נמצא או יש שגיאה, ניצור ספר חדש
//            System.out.println("Failed to load opening book. Starting with an empty book.");
//        }
//    }

    // constructor:
    public myEngine(BoardState board, CountDownLatch latch) {
        setBoard(board);
        this.latch = latch;
    }

    public myEngine(BoardState board) {
        setBoard(board);
        this.latch = new CountDownLatch(1);
    }

    // making move methods
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Future<Void> makeMove(String fen, Board realBoard) {
        Callable<Void> task = () -> {
            try {
                if (SettingPanel.skillLevel == 0) {
                    Thread.sleep(waitTime);
                }
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }

                this.fen = fen;
                Move move = chooseMethod(board);
                while (move == null) {
                    move = chooseMethod(board);
                }
                Move tempMove = move;
                if (board.makeMoveToCheckIt(tempMove)) {
                    realBoard.makeMove(move);
                    if (realBoard.input.isStatusChanged) {
                        Board.selectedPiece = null;
                        realBoard.repaint();
                        SwingUtilities.invokeLater(() -> {
                            JFrame frame = new JFrame("Game Over");
                            realBoard.updateGameState(true);
                            Main.showEndGameMessage(frame, (realBoard.input.isCheckMate ? (realBoard.input.isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") : (realBoard.input.isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "המשחק נגמר בתיקו.")));
                        });
                    }
                    alreadyChecked.clear();
                }
                else {
                    System.out.println("retrying...");
                    int temp = SettingPanel.skillLevel;
                    SettingPanel.skillLevel = 0;
                    waitTime = 0;
                    do {
                        move = chooseMethod(board);
                    } while (move == null);
                    tempMove = move;
                    if (board.makeMoveToCheckIt(tempMove)) {
                        realBoard.makeMove(move);
                        if (realBoard.input.isStatusChanged) {
                            Board.selectedPiece = null;
                            realBoard.repaint();
                            SwingUtilities.invokeLater(() -> {
                                JFrame frame = new JFrame("Game Over");
                                realBoard.updateGameState(true);
                                Main.showEndGameMessage(frame, (realBoard.input.isCheckMate ?
                                        (realBoard.input.isWhiteTurn ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!") :
                                        (realBoard.input.isStaleMate ? "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו" : "אין חומר מספיק. המשחק נגמר בתיקו.")));
                            });
                        }
                    }
                    alreadyChecked.clear();
                    waitTime = 1000;
                    SettingPanel.skillLevel = temp;
                }
            } catch (InterruptedException e) {
                // Handle interruption
            }
            return null;
        };
        return executor.submit(task);
    }

    public Future<Void> giveHint(String fen, Board realBoard) {
        Callable<Void> task = () -> {
            try {
                if (SettingPanel.skillLevel == 0) {
                    Thread.sleep(waitTime);
                }
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
                this.fen = fen;
                Move move = chooseMethod(board);
                while (move == null) {
                    move = chooseMethod(board);
                }
                Move tempMove = move;
                if (board.makeMoveToCheckIt(tempMove)) {
                    realBoard.hintToC = tempMove.newCol;
                    realBoard.hintToR = tempMove.newRow;
                    realBoard.hintFromC = tempMove.piece.col;
                    realBoard.hintFromR = tempMove.piece.row;
                    alreadyChecked.clear();
                } else {
                    System.out.println("no hint found");
                }
            } catch (InterruptedException e) {
                // Handle interruption
            }
            return null;
        };
        return executor.submit(task);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private Move chooseMethod(BoardState board) {
        int skillLevel = SettingPanel.skillLevel;
        if (skillLevel == 0) {
            return getRandomMove();
        } else {
            return new Move(board, getBestMove());
        }
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    // random move methods
    private Move getRandomMove() {
        if (board == null) {
            System.out.println("BoardState is null");
            return null;
        }

        if (!board.checkScanner.isChecking(board)) {
            chosePiece();
        } else {
            for (int i = 0; i < board.getNumOfPieces(board.getIsWhiteToMove()); i++) {
                this.chosenPiece = board.getPieceByNumber(i, board.getIsWhiteToMove());
                if (chosenPiece.isWhite == board.getIsWhiteToMove()) {
                    this.randomMovesList = chosenPiece.getValidMoves(board);
                    if (!randomMovesList.isEmpty()) {
                        break;
                    }
                }
            }
        }
        if (randomMovesList == null || randomMovesList.isEmpty()) {
            System.out.println("No valid moves available");
            return null;
        }
        int randomNumOfMove = (int) (Math.random() * randomMovesList.size());
        Move randomMove = randomMovesList.get(randomNumOfMove);
        if (randomMove.piece.name.equals("Pawn") && board.getIsWhiteToMove() ? randomMove.newRow == 0 : randomMove.newRow == 7) {
            setPromotionChoice();
        } else {
            promotionChoice = null;
        }
        return randomMove;
    }

    private void setPromotionChoice() {
        int randomPromotionChoiceNum = (int) (Math.random() * 4);
        switch (randomPromotionChoiceNum) {
            case 1:
                promotionChoice = "r";
                break;
            case 2:
                promotionChoice = "b";
                break;
            case 3:
                promotionChoice = "n";
                break;
            default:
                promotionChoice = "q";
                break;
        }
    }

    private void chosePiece() {
        int randomNumOfPiece = (int) (Math.random() * board.getNumOfPieces(board.getIsWhiteToMove()));
        this.chosenPiece = board.getPieceByNumber(randomNumOfPiece, board.getIsWhiteToMove());
        if (chosenPiece != null && !alreadyChecked.contains(chosenPiece)) {
            this.randomMovesList = chosenPiece.getValidMoves(board);
            if (randomMovesList.isEmpty()) {
                alreadyChecked.add(chosenPiece);
                chosePiece();
            }
        } else {
            chosePiece();
        }
    }

    // other engine methods
    private BitMove getBestMove() {
        // בדיקת מצב המשחק והשפעתו על העומק המקסימלי של האלגוריתם
        if (board.getGameState() == 10) {
            Minimax.maxDepth = SettingPanel.skillLevel / 2 + 2;
            System.out.println("+2 depth");
        } else if (board.getGameState() == 2) {
            System.out.println("+1 depth");
            Minimax.maxDepth = SettingPanel.skillLevel / 2 + 1;
        } else {
            Minimax.maxDepth = SettingPanel.skillLevel / 2;
        }

        // בדיקת רמת המיומנות לשימוש בספר הפתיחות
//        if (SettingPanel.skillLevel >= 5) {
//            String currentFEN = board.convertPiecesToFEN();
//            List<String> openingMoves = openingBook.getMoves(currentFEN);
//
//            if (openingMoves != null && !openingMoves.isEmpty()) {
//                // בחירת מהלך אקראי מתוך ספר הפתיחות
//                String chosenMove = openingMoves.get((int) (Math.random() * openingMoves.size()));
//                System.out.println("Opening book move chosen: " + chosenMove);
//                return convertToBitMove(chosenMove); // כאן תוסיף את הקוד להמרת מהלך ל-BitMove
//            }
//        }

        // אם אין מהלך בספר הפתיחות, המנוע יחשב מהלך רגיל
        // System.out.println("Calculating engine move. Depth: " + Minimax.maxDepth);
        BitMove move = Minimax.getBestMove(board);
        promotionChoice = String.valueOf(move.promotionChoice);
        return move;
    }

    private BitMove convertToBitMove(String chosenMove) {
        return null;
    }

    // board setter
    public void setBoard(BoardState state) {
        this.board = state;
    }

}
