package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitMove;
import ai.BitBoard.ZobristHashing;
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
        BitBoard bitboard = new BitBoard(board);
        BitMove bestMove = null;
        Instant start, end;
        long timeElapsed;

        // יצירת מופע של BoardStateTracker וטבלת טרנספוזיציות
        BoardStateTracker boardStateTracker = new BoardStateTracker();
        TranspositionTable transpositionTable = new TranspositionTable();

        for (int depth = maxDepth; depth <= maxDepth; depth++) {
            start = Instant.now();
            MinimaxResult result = minimax(bitboard, depth, true, Integer.MIN_VALUE, Integer.MAX_VALUE, boardStateTracker, transpositionTable);
            end = Instant.now();
            timeElapsed = Duration.between(start, end).toMillis();

            if (depth == maxDepth) {
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

    private static MinimaxResult minimax(BitBoard board, int depth, boolean isMaximizingPlayer, int alpha, int beta, BoardStateTracker boardStateTracker, TranspositionTable transpositionTable) {
        // long zobristHash = ZobristHashing.computeHash(board);

        // בדיקה אם המצב כבר קיים בטבלת טרנספוזיציות
//        TranspositionTable.TranspositionTableEntry entry = transpositionTable.get(zobristHash);
//        if (entry != null && entry.depth >= depth) {
//            System.out.println("this state has already been checked. returning saved result");
//            return new MinimaxResult(entry.bestMove, entry.value);
//        }

        boardStateTracker.addBoardState(board);

        if (depth == 0 || board.getStatus() != 1 || boardStateTracker.isThreefoldRepetition()) {
            boardStateTracker.removeLastBoardState();
            return new MinimaxResult(board.lastMove, BitBoardEvaluate.evaluate(board));
        }

        BitMove bestMove = board.getRandomPossibleMove();
        boolean lastDepth = depth == maxDepth;
        if (lastDepth) {
            bestMoves = new ArrayList<>();
        }
        int bestValue;

        if (isMaximizingPlayer) {
            bestValue = Integer.MIN_VALUE;
            for (BitBoard state : board.getNextStates()) {
                MinimaxResult result = minimax(state, depth - 1, false, alpha, beta, boardStateTracker, transpositionTable);

                if (result.value > bestValue) {
                    bestValue = result.value;
                    bestMove = state.lastMove;
                    if (lastDepth) {
                        bestMoves.clear();
                        bestMoves.add(bestMove);
                    }
                } else if (lastDepth && result.value == bestValue && (!bestMoves.contains(result.move))) {
                    bestMoves.add(bestMove);
                }
                alpha = Math.max(alpha, bestValue);
                if (beta <= alpha) {
                    break; // אלפא-בטא גיזום
                }
            }
        } else {
            bestValue = Integer.MAX_VALUE;
            for (BitBoard state : board.getNextStates()) {
                MinimaxResult result = minimax(state, depth - 1, true, alpha, beta, boardStateTracker, transpositionTable);

                if (result.value < bestValue) {
                    bestValue = result.value;
                    bestMove = state.lastMove;
                }
                beta = Math.min(beta, bestValue);
                if (beta <= alpha) {
                    break; // אלפא-בטא גיזום
                }
            }
        }

        boardStateTracker.removeLastBoardState();
//
//        // שמירת התוצאה בטבלת טרנספוזיציות
//        transpositionTable.put(zobristHash, depth, bestValue, bestMove);

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
