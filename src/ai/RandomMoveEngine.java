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
    public String promotionChoice;

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
            board.loadPiecesFromFen(fen, false);
            if (!board.checkScanner.isChecking(board)) {
                chosePiece();
            }
            else {
                // System.out.println("check");
                for (int i = 0; i < board.getNumOfPieces(board.getIsWhiteToMove()); i++) {
                    this.chocenPiece = board.getPieceByNumber(i, board.getIsWhiteToMove());
                    if (chocenPiece.isWhite == board.getIsWhiteToMove()) {
                        // System.out.println(chosenPiece + " col: " + chosenPiece.col + " row: " + chosenPiece.row);
                        this.randomMovesList = chocenPiece.getValidMoves(board);
                        // נוודא שהמהלכים לא מכניסים את המלך לשח
                        //randomMovesList.removeIf(move -> board.checkScanner.isMoveCausesCheck(new Move(board, chocenPiece, move.newCol, move.newRow)));
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
            if (randomMove.piece.name.equals("Pawn") && board.getIsWhiteToMove() ? randomMove.newRow == 0 : randomMove.newRow == 7) {
                setPromotionChoice();
            }
            Move tempMove = randomMove;
            //System.out.println(fen);
            if (board.makeMoveToCheckIt(tempMove)) {
                board.makeMove(randomMove, true);
                board.input.selectedX = -1;
                board.input.selectedY = -1;
                board.selectedPiece = null;
                board.repaint();
                alreadyChecked.clear();
            }
            else {
                makeMove(fen);
            }
        }).start();
    }

    private void setPromotionChoice() {
        int randomPromotionChoiceNum = (int) (Math.random() * 4);
        switch (randomPromotionChoiceNum) {
            case 1: promotionChoice = "r";
            break;
            case 2: promotionChoice = "b";
            break;
            case 3: promotionChoice = "n";
            break;
            default: promotionChoice = "q";
            break;
        }
    }

    private void chosePiece() {
        this.randomNumOfPiece = (int) (Math.random() * board.getNumOfPieces(board.getIsWhiteToMove()));
        //System.out.println(randomNumOfPiece);
        this.chocenPiece = board.getPieceByNumber(randomNumOfPiece, board.getIsWhiteToMove());
        //System.out.println(chocenPiece);
        if (chocenPiece != null && !alreadyChecked.contains(chocenPiece) /*&& chocenPiece.name.equals("Pawn")*/) { //נועד לבדיקת הכתרות
            this.randomMovesList = chocenPiece.getValidMoves(board);
            // נוודא שהמהלכים לא מכניסים את המלך לשח
            //randomMovesList.removeIf(move -> board.checkScanner.isMoveCausesCheck(new Move(board, chocenPiece, move.newCol, move.newRow)));
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