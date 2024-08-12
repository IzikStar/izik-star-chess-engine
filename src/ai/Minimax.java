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
    public static int prunings = 0;
    public static int nodesChecked = 0;
    public static int nodesInMaxDepth = 0;

    public static BitMove getBestMove(BoardState board) {
        prunings = 0;
        nodesChecked = 0;
        nodesInMaxDepth = 0;
        BitBoard bitboard = new BitBoard(board);
        // System.out.println("sortes: " + bitboard.getSortedNextStates().size() + " unsorted: " + bitboard.getNextStates().size());
        getNumOfNodes(bitboard, maxDepth);
        BitMove bestMove = null;
        Instant start, end;
        long timeElapsed = 0;

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
                    System.out.println("best moves size: " + bestMoves.size());
                    // System.out.println("best moves: " + bestMoves);
                    int randomIndex = random.nextInt(bestMoves.size());
                    result.move = bestMoves.get(randomIndex);
                }
            }

            if (result.move != null) {
                bestMove = result.move;
            }
        }
        // System.out.println("prunings: " + prunings);
        System.out.println("num of nodes: " + nodesInMaxDepth);
        System.out.println("nodes checked: " + nodesChecked);
        System.out.println("time: " + timeElapsed);
        return bestMove;
    }

    private static void getNumOfNodes(BitBoard board, int depth) {
        nodesInMaxDepth += board.getNextStates().size();
        if (depth == 1) return;
        for (BitBoard bitBoard : board.getNextStates()) {
            getNumOfNodes(board, depth - 1);
        }
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
        if (depth == 0 || board.getStatus() != 1) {
            boardStateTracker.removeLastBoardState();
            nodesChecked++;
            return new MinimaxResult(board.lastMove, BitBoardEvaluate.evaluate(board));
        }

        if (boardStateTracker.isThreefoldRepetition()) {
            boardStateTracker.removeLastBoardState();
            return new MinimaxResult(board.lastMove, 0);
        }

        BitMove bestMove = board.getRandomPossibleMove();
        boolean lastDepth = (depth == maxDepth && ChoosePlayFormat.isPlayingWhite) == (!board.getIsWhiteToMove());
        if (lastDepth) {
            bestMoves = new ArrayList<>();
        }
        int bestValue;

        if (isMaximizingPlayer) {
            bestValue = Integer.MIN_VALUE;
            for (BitBoard state : board.getSortedNextStates()) {
                MinimaxResult result = minimax(state, depth - 1, false, alpha, beta, boardStateTracker, transpositionTable);

                if (result.value > bestValue) {
                    bestMove = state.lastMove;
                    bestValue = result.value;
                    if (lastDepth) {
                        // if (result.value - bestValue > 3)
                        bestMoves.clear();
                        bestMoves.add(bestMove);
                    }
                } else if (lastDepth && result.value == bestValue && (!alreadyAdded(result.move))) {
                    // bestMoves.add(bestMove);
                    // System.out.println(bestMove);
                }
                alpha = Math.max(alpha, bestValue);
                if (beta <= alpha) {
                    // prunings += 1 * (maxDepth - depth);
                    break; // אלפא-בטא גיזום
                }
            }
        } else {
            bestValue = Integer.MAX_VALUE;
            for (BitBoard state : board.getSortedNextStates()) {
                MinimaxResult result = minimax(state, depth - 1, true, alpha, beta, boardStateTracker, transpositionTable);

                if (result.value < bestValue) {
                    bestValue = result.value;
                    bestMove = state.lastMove;
                }
                beta = Math.min(beta, bestValue);
                if (beta <= alpha) {
                    // prunings += 1 * (maxDepth - depth);
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

    private static boolean alreadyAdded(BitMove move) {
        for (BitMove bitMove : bestMoves) {
            if (bitMove.toString().equals(move.toString())) return true;
            // System.out.println("compared " + bitMove + " to " + move);
        }
        return false;
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
        maxDepth = 5;
        String fen = "rk6/p1p5/B4p2/1q2bP2/3N4/2K5/8/1R6 b - - 0 1";
        BoardState boardState = new BoardState(fen, null);
        BitBoard bitBoard = new BitBoard(boardState);
        System.out.println("sortes: " + bitBoard.getSortedNextStates().size() + " unsorted: " + bitBoard.getNextStates().size());
        // System.out.println("best move final: " + getBestMove(boardState));
        getBestMove(boardState);
    }

}
