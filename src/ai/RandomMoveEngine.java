package ai;

import main.Board;
import main.Move;
import pieces.Piece;
import java.util.ArrayList;

public class RandomMoveEngine {
    Board board;
    private int randomNumOfPiece;
    private int randomNumOfMove;
    public Piece chosenPiece;
    private ArrayList<Move> randomMovesList;
    private ArrayList<Piece> alreadyChecked = new ArrayList<>();
    public static int waitTime = 1000;
    private String fen;
    public String promotionChoice;
    private Thread thread;

    public RandomMoveEngine(Board board) {
        this.board = board;
    }

    public void makeMove(String fen) {
        board.selectedPiece = null;
        thread = new Thread(() -> {
            try {
                Thread.sleep(waitTime);
                if (Thread.currentThread().isInterrupted()) {
                    return; // בדיקה אם ה-Thread הופסק
                }
            } catch (InterruptedException e) {
                return; // יציאה מה-Thread במקרה של הפסקה
            }
            this.fen = fen;
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
                return;
            }
            this.randomNumOfMove = (int) (Math.random() * randomMovesList.size());
            Move randomMove = randomMovesList.get(randomNumOfMove);
            if (randomMove.piece.name.equals("Pawn") && board.getIsWhiteToMove() ? randomMove.newRow == 0 : randomMove.newRow == 7) {
                setPromotionChoice();
            }
            Move tempMove = randomMove;
            if (board.makeMoveToCheckIt(tempMove)) {
                board.makeMove(randomMove, true);
                board.input.selectedX = -1;
                board.input.selectedY = -1;
                alreadyChecked.clear();
            } else {
                makeMove(fen);
            }
        });
        thread.start();
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
        }
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
        this.randomNumOfPiece = (int) (Math.random() * board.getNumOfPieces(board.getIsWhiteToMove()));
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
}
