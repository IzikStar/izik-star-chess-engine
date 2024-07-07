package ai;

import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.Piece;

import java.util.ArrayList;

public class Minimax {

    public static Move getBestMove(BoardState board, int depth) {
        BoardState.numOfNodes = 1;
        Move bestMove = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;
        BoardState cloneBoard = board;
        String fen = cloneBoard.convertPiecesToFEN();

        for (Move move : cloneBoard.getAllPossibleMoves()) {
            if (!(cloneBoard.getPiece(move.piece.col, move.piece.row) == null)) {
                cloneBoard.makeMove(move);
                double boardValue = minimax(cloneBoard, depth - 1, false, alpha, beta);
                cloneBoard.loadPiecesFromFen(fen);
                System.out.printf("Move: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f", move.piece.col, move.piece.row, move.newCol, move.newRow, boardValue, alpha, beta); // בדיקת ערכים
                if (boardValue > bestValue) {
                    bestValue = boardValue;
                    bestMove = move;
                }
            }
        }

        System.out.printf("Best Move: %s, Best Value: %.2f\n", bestMove, bestValue); // בדיקת המהלך הטוב ביותר
        return bestMove;
    }

    private static double minimax(BoardState board, int depth, boolean isMaximizingPlayer, double alpha, double beta) {
        if (depth == 0) {
            return EvaluationLevel2.evaluate(board);
        }
        String fen = board.convertPiecesToFEN();
        if (isMaximizingPlayer) {
            double bestValue = Double.NEGATIVE_INFINITY;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        board.makeMove(move);
                        double boardValue = minimax(board, depth - 1, false, alpha, beta);
                        board.loadPiecesFromFen(fen);
                        System.out.printf("Maximizing: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f", move.piece.col, move.piece.row, move.newCol, move.newRow, boardValue, alpha, beta); // בדיקת ערכים
                        bestValue = Math.max(bestValue, boardValue);
                        alpha = Math.max(alpha, boardValue);
                        if (alpha >= beta) {
                            System.out.printf("Pruning at move: %d, %d to %d, %d, Alpha: %.2f, Beta: %.2f", move.piece.col, move.piece.row, move.newCol, move.newRow, alpha, beta); // בדיקת גיזום
                            break; // אלפא-בטא גיזום
                        }
                    }
                }
            }
            return bestValue;
        } else {
            double bestValue = Double.POSITIVE_INFINITY;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite == ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        board.makeMove(move);
                        double boardValue = minimax(board, depth - 1, true, alpha, beta);
                        board.loadPiecesFromFen(fen);
                        System.out.printf("Minimizing: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f", move.piece.col, move.piece.row, move.newCol, move.newRow, boardValue, alpha, beta); // בדיקת ערכים
                        bestValue = Math.min(bestValue, boardValue);
                        beta = Math.min(beta, boardValue);
                        if (alpha >= beta) {
                            System.out.printf("Pruning at %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f", move.piece.col, move.piece.row, move.newCol, move.newRow, alpha, beta); // בדיקת גיזום
                            break; // אלפא-בטא גיזום
                        }
                    }
                }
            }
            return bestValue;
        }
    }

}
