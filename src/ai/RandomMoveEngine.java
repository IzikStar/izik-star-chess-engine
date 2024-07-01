package ai;

import main.Board;
import main.Main;
import main.Move;
import main.setting.ChoosePlayFormat;
import main.setting.SettingPanel;
import pieces.Piece;

import javax.swing.*;
import java.util.ArrayList;

public class RandomMoveEngine {
    Board board;
    private int randomNumOfPiece;
    private int randomNumOfMove;
    public Piece chocenPiece;
    private ArrayList<Move> randomMovesList;
    private ArrayList<Piece> alreadyChecked = new ArrayList<>();
    public static int waitTime = 1000;
    private String fen;

    public RandomMoveEngine(Board board) {
        this.board = board;
    }

    public void makeMove(String fen) {
        new Thread(() -> {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.fen = fen;
            board.loadPiecesFromFen(fen);
            if (!board.checkScanner.isChecking(board)) {
                chosePiece();
            }
            else {
                // System.out.println("check");
                for (int i = 0; i < board.getNumOfPieces(); i++) {
                    this.chocenPiece = board.getPieceByNumber(i);
                    if (chocenPiece.isWhite == board.getIsWhiteToMove()) {
                        // System.out.println(chosenPiece + " col: " + chosenPiece.col + " row: " + chosenPiece.row);
                        this.randomMovesList = chocenPiece.getValidMoves(board);
                        if ((!randomMovesList.isEmpty()) /*&& chocenPiece.name.equals("King")*/) {
                            break;
                        }
                    }
                }
            }
            if (randomMovesList == null || randomMovesList.isEmpty()) {
                // לא נמצאו מהלכים חוקיים. טיפול מתאים, כמו הדפסת הודעת שגיאה או סיום המשחק
                System.out.println("No valid moves available");
                return;
            }
            this.randomNumOfMove = (int) (Math.random() * randomMovesList.size());
            Move randomMove = randomMovesList.get(randomNumOfMove);
            board.makeMove(randomMove);
            alreadyChecked.clear();
        }).start();
    }

    private void chosePiece() {
        this.randomNumOfPiece = (int) (Math.random() * board.getNumOfPieces());
        //System.out.println(randomNumOfPiece);
        this.chocenPiece = board.getPieceByNumber(randomNumOfPiece);
        //System.out.println(chocenPiece);
        if (chocenPiece != null && !alreadyChecked.contains(chocenPiece)) {
            this.randomMovesList = chocenPiece.getValidMoves(board);
            if (randomMovesList.isEmpty()) {
                alreadyChecked.add(chocenPiece);
                chosePiece();
            }
        }
        else {
            chosePiece();
        }
    }

}