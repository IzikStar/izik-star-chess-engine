package ai;

import main.Board;
import main.Move;
import pieces.Piece;

import java.util.ArrayList;

public class RandomMoveEngine {
    Board board = new Board();
    private int randomNumOfPiece;
    private int randomNumOfMove;
    public Piece chocenPiece;
    private ArrayList<Move> randomMovesList;

    public RandomMoveEngine(Board board) {
        this.board = board;
        chosePiece();
        this.randomNumOfMove = (int) (Math.random() * randomMovesList.size());
    }
    public void makeMove() {
        Move randomMove = randomMovesList.get(randomNumOfMove);
        board.makeMove(randomMove);
    }
    private void chosePiece() {
        this.randomNumOfPiece = (int) (Math.random() * board.getNumOfPieces());
        System.out.println(randomNumOfPiece);
        this.chocenPiece = board.getPieceByNumber(randomNumOfPiece);
        System.out.println(chocenPiece);
        if (chocenPiece != null) {
            this.randomMovesList = chocenPiece.getValidMoves(board);
            if (randomMovesList.isEmpty()) {
                chosePiece();
            }
        }
        else {
            chosePiece();
        }
    }
}