package ai;

import main.Board;
import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.Piece;

import java.util.ArrayList;

public class Minimax {

    public static Move getBestMove(Board board, int depth) {
        Move bestMove = null;
        int bestValue = Integer.MIN_VALUE;

        for (Piece piece : board.getAllPieces()) {
            if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                ArrayList<Move> validMoves = piece.getValidMoves(board);
                for (Move move : validMoves) {
                    BoardClone newBoard = new BoardClone(board.convertPiecesToFEN()); // שיבוץ לוח משוכפל באמצעות FEN
                    newBoard.makeMoveOnCloneBoard(move);
                    int boardValue = minimax(newBoard, depth - 1, false);
                    if (boardValue > bestValue) {
                        bestValue = boardValue;
                        bestMove = move;
                    }
                }
            }
        }

        return bestMove;
    }

    private static int minimax(Board board, int depth, boolean isMaximizingPlayer) {
        if (depth == 0) {
            return EvaluationLevel2.evaluate(board);
        }

        if (isMaximizingPlayer) {
            int bestValue = Integer.MIN_VALUE;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        BoardClone newBoard = new BoardClone(board.convertPiecesToFEN());
                        newBoard.makeMoveOnCloneBoard(move);
                        int boardValue = minimax(newBoard, depth - 1, false);
                        bestValue = Math.max(bestValue, boardValue);
                    }
                }
            }
            return bestValue;
        } else {
            int bestValue = Integer.MAX_VALUE;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite == ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        BoardClone newBoard = new BoardClone(board.convertPiecesToFEN());
                        newBoard.makeMoveOnCloneBoard(move);
                        int boardValue = minimax(newBoard, depth - 1, true);
                        bestValue = Math.min(bestValue, boardValue);
                    }
                }
            }
            return bestValue;
        }
    }
}
