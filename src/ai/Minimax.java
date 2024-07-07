package ai;

import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.Piece;

import java.util.ArrayList;

public class Minimax {

    public static Move getBestMove(BoardState board, int depth) {
        Move bestMove = null;
        int bestValue = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Piece piece : board.getAllPieces()) {
            if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                ArrayList<Move> validMoves = piece.getValidMoves(board);
                for (Move move : validMoves) {
                    String fen = board.makeMoveAndGetFen(move);
                    BoardState newBoard = new BoardState(fen, move); // שיבוץ לוח משוכפל באמצעות FEN
                    int boardValue = minimax(newBoard, depth - 1, false, alpha, beta);
                    if (boardValue > bestValue) {
                        bestValue = boardValue;
                        bestMove = move;
                    }
                }
            }
        }

        return bestMove;
    }

    private static int minimax(BoardState board, int depth, boolean isMaximizingPlayer, int alpha, int beta) {
        if (depth == 0 || alpha > beta) {
            return EvaluationLevel2.evaluate(board);
        }

        if (isMaximizingPlayer) {
            int bestValue = Integer.MIN_VALUE;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        String fen = board.makeMoveAndGetFen(move);
                        BoardState newBoard = new BoardState(fen, move);
                        int boardValue = minimax(newBoard, depth - 1, false, alpha, beta);
                        bestValue = Math.max(bestValue, boardValue);
                        alpha = Math.max(alpha, boardValue);
                        if (alpha >= beta) {
                            break;
                        }
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
                        String fen = board.makeMoveAndGetFen(move);
                        BoardState newBoard = new BoardState(fen, move);
                        int boardValue = minimax(newBoard, depth - 1, true, alpha, beta);
                        bestValue = Math.min(bestValue, boardValue);
                        beta = Math.min(beta, boardValue);
                        if (alpha >= beta) {
                            break;
                        }
                    }
                }
            }
            return bestValue;
        }
    }

}
