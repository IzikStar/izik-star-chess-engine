package ai;

import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.Piece;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

public class Minimax {

    public static Move getBestMove(BoardState board, int depth) {
        if (ChoosePlayFormat.isPlayingWhite != board.getIsWhiteToMove()) {
            BoardState.numOfNodes = 1;
            String tempFen = board.convertPiecesToFEN();
            BoardState cloneBoard = new BoardState(tempFen, board.lastMove);
            Instant start, end;
            long timeElapsed;
            start = Instant.now(); // התחלת מדידת זמן
            MinimaxResult result = minimax(cloneBoard, depth, true, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            end = Instant.now(); // סיום מדידת זמן
            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
            System.out.printf("Best Move: %s, Best Value: %.2f, time spend: %d ms\n", result.move, result.value, timeElapsed);
            return result.move;
        }
        return null;
    }

    private static MinimaxResult minimax(BoardState board, int depth, boolean isMaximizingPlayer, double alpha, double beta) {
        if (depth == 0) {
            return new MinimaxResult(null, EvaluationLevel2.evaluate(board));
        }
        String fen = board.convertPiecesToFEN();
        Move bestMove = null;
        double bestValue;
        Instant start, end;
        long timeElapsed;

        if (isMaximizingPlayer) {
            bestValue = Double.NEGATIVE_INFINITY;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        start = Instant.now(); // התחלת מדידת זמן
                        if (board.makeMoveToCheckIt(move)) {
                            board.makeMove(move);
                            MinimaxResult result = minimax(board, depth - 1, false, alpha, beta);
                            board.loadPiecesFromFen(fen);
                            end = Instant.now(); // סיום מדידת זמן
                            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                            System.out.printf("Maximizing: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f, Time: %d ms\n", move.piece.col, move.piece.row, move.newCol, move.newRow, result.value, alpha, beta, timeElapsed);
                            if (result.value > bestValue) {
                                bestValue = result.value;
                                bestMove = move;
                            }
                            alpha = Math.max(alpha, bestValue);
                            if (beta <= alpha) {
                                System.out.printf("Pruning at move: %d, %d to %d, %d, Alpha: %.2f, Beta: %.2f\n", move.piece.col, move.piece.row, move.newCol, move.newRow, alpha, beta);
                                break; // אלפא-בטא גיזום
                            }
                        }
                    }
                }
            }
        } else {
            bestValue = Double.POSITIVE_INFINITY;
            for (Piece piece : board.getAllPieces()) {
                if (piece.isWhite == ChoosePlayFormat.isPlayingWhite) {
                    ArrayList<Move> validMoves = piece.getValidMoves(board);
                    for (Move move : validMoves) {
                        start = Instant.now(); // התחלת מדידת זמן
                        if (board.makeMoveToCheckIt(move)) {
                            board.makeMove(move);
                            MinimaxResult result = minimax(board, depth - 1, true, alpha, beta);
                            board.loadPiecesFromFen(fen);
                            end = Instant.now(); // סיום מדידת זמן
                            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                            System.out.printf("Minimizing: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f, Time: %d ms\n", move.piece.col, move.piece.row, move.newCol, move.newRow, result.value, alpha, beta, timeElapsed);
                            if (result.value < bestValue) {
                                bestValue = result.value;
                                bestMove = move;
                            }
                            beta = Math.min(beta, bestValue);
                            if (beta <= alpha) {
                                System.out.printf("Pruning at move: %d, %d to %d, %d, Alpha: %.2f, Beta: %.2f\n", move.piece.col, move.piece.row, move.newCol, move.newRow, alpha, beta);
                                break; // אלפא-בטא גיזום
                            }
                        }

                    }
                }
            }
        }

        return new MinimaxResult(bestMove, bestValue);
    }

    private static class MinimaxResult {
        Move move;
        double value;

        MinimaxResult(Move move, double value) {
            this.move = move;
            this.value = value;
        }
    }
}
