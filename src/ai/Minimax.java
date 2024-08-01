package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitBoardOperations;
import ai.BitBoard.BitMove;
import main.setting.ChoosePlayFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;

public class Minimax {
    private static final Random random = new Random();
    public static ArrayList<BitMove> bestMoves;
    public static int maxDepth;

    public static BitMove getBestMove(BoardState board) {
        if (ChoosePlayFormat.isPlayingWhite != board.getIsWhiteToMove()) {
            BitBoard bitboard = new BitBoard(board);
            BitMove bestMove = null;
            Instant start, end;
            long timeElapsed;

            for (int depth = maxDepth; depth <= maxDepth; depth++) {
                start = Instant.now(); // התחלת מדידת זמן
                MinimaxResult result = minimax(bitboard, depth, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
                end = Instant.now(); // סיום מדידת זמן
                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                // System.out.printf("Depth: %d, Best Move: %s, Best Value: %d, Time Spend: %d ms\n", depth, result.move, result.value, timeElapsed);

                if (depth == maxDepth) {
                    // System.out.println("best moves: " + bestMoves + " size: " + bestMoves.size());
                    if (!bestMoves.isEmpty()) {
                        int randomIndex = random.nextInt(bestMoves.size());
                        result.move = bestMoves.get(randomIndex);
                    }
                }

                if (result.move != null) {
                    bestMove = result.move;
                }
            }
            return bestMove;
        }
        return null;
    }

    private static MinimaxResult minimax(BitBoard board, int depth, boolean isMaximizingPlayer, int alpha, int beta) {
        if (depth == 0 || board.getStatus() != 1) {
            return new MinimaxResult(board.lastMove, BitBoardEvaluate.evaluate(board));
        }
        BitMove bestMove = board.getRandomPossibleMove();
        boolean lastDepth = depth == maxDepth;
        if (lastDepth) {
            bestMoves = new ArrayList<>();
        }
        int bestValue;
        Instant start, end;
        long timeElapsed;

        if (isMaximizingPlayer) {
            bestValue = Integer.MIN_VALUE;
            for (BitBoard state : board.getNextStates()) {
                start = Instant.now(); // התחלת מדידת זמן
                MinimaxResult result = minimax(state, depth - 1, false, alpha, beta);
                end = Instant.now(); // סיום מדידת זמן
                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                // System.out.printf("Maximizing: Value: %d, Alpha: %d, Beta: %d, Time: %d ms\n", result.value, alpha, beta, timeElapsed);
                if (result.value > bestValue) {
                    // System.out.println("board:" + board);
                    // System.out.println("best state" + state);
                    bestValue = result.value;
                    bestMove = state.lastMove;
                    if (lastDepth) {
                        bestMoves.clear();
                        bestMoves.add(bestMove);
                        // System.out.println("move added, turn: " + (board.getIsWhiteToMove() ? "white." : "black.") + " depth: " + depth);
                    }
                } else if (lastDepth && result.value == bestValue && (!bestMoves.contains(result.move))) {
                    bestMoves.add(bestMove);
                }
                alpha = Math.max(alpha, bestValue);
                if (beta <= alpha) {
                    // System.out.printf("Pruning at state: %s Alpha: %d, Beta: %d\n", state, alpha, beta);
                    break; // אלפא-בטא גיזום
                }
            }
        }
        else {
            bestValue = Integer.MAX_VALUE;
            for (BitBoard state : board.getNextStates()) {
                start = Instant.now(); // התחלת מדידת זמן
                MinimaxResult result = minimax(state, depth - 1, true, alpha, beta);
                end = Instant.now(); // סיום מדידת זמן
                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                // System.out.printf("Minimizing: Value: %d, Alpha: %d, Beta: %d, Time: %d ms\n", result.value, alpha, beta, timeElapsed);
                if (result.value < bestValue) {
                    bestValue = result.value;
                    bestMove = state.lastMove;
                }
                beta = Math.min(beta, bestValue); // תיקון כאן
                if (beta <= alpha) {
                    // System.out.printf("Pruning at state: %s Alpha: %d, Beta: %d\n", state, alpha, beta);
                    break; // אלפא-בטא גיזום
                }
            }
        }
        return new MinimaxResult(bestMove, bestValue);
    }

    private static class MinimaxResult {
        BitMove move;
        int value;

        MinimaxResult(BitMove move, int value) {
            this.move = move;
            this.value = value;
        }
    }

    public static void main(String[] args) {
        maxDepth = 2;
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";
        BoardState boardState = new BoardState(fen, null);
        System.out.println("best move final: " + getBestMove(boardState));
    }

}
