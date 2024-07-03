package ai;

import main.Board;
import main.Move;
import pieces.Piece;

import java.util.ArrayList;

public class Level2Engine {

    Board board;
    public Piece chocenPiece;
    public Move bestMove;
    private ArrayList<Move> MovesList;
    private static final int DEPTH = 1;
    public static int waitTime = 1000;

    public Level2Engine(Board board) {
        this.board = board;
    }

    public void makeMove(Board board) {
        new Thread(() -> {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //if (!board.checkScanner.isChecking(board)) {
                getBestMove();
                board.makeMove(bestMove, true);
            //}
//            else {
//                // System.out.println("check");
//                for (int i = 0; i < board.getNumOfPieces(); i++) {
//                    this.chocenPiece = board.getPieceByNumber(i);
//                    // System.out.println(chosenPiece + " col: " + chosenPiece.col + " row: " + chosenPiece.row);
//                    this.MovesList = chocenPiece.getValidMoves(board);
//                    if (!MovesList.isEmpty()) {
//                        break;
//                    }
//                }
//            }
            if (MovesList == null || MovesList.isEmpty()) {
                // לא נמצאו מהלכים חוקיים. טיפול מתאים, כמו הדפסת הודעת שגיאה או סיום המשחק
                System.out.println("No valid moves available");
                return;
            }
        }).start();
    }

    public void getBestMove() {
        bestMove = Minimax.getBestMove(board, DEPTH);
    }


}
