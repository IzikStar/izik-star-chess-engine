package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitMove;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;

/**
 * Alpha-beta search over the bitboard. Each call to {@link #getBestMove(BoardState, int)} runs on
 * its own instance, so no search state survives between searches, and the search always chooses
 * a move for the side to move at the root — it reads no UI settings (Phase 3; the old version
 * inferred "am I at the root" from {@code ChoosePlayFormat}, which broke whenever those flags were
 * flipped around an engine call).
 */
public class Minimax {
    private static final Random random = new Random();

    private final int searchDepth;
    /** True when the side choosing the move (the side to move at the root) is Black. */
    private final boolean rootIsBlack;
    private final ArrayList<BitMove> bestMoves = new ArrayList<>();
    private int nodesChecked = 0;
    private int nodesInMaxDepth = 0;

    private Minimax(int searchDepth, boolean rootIsBlack) {
        this.searchDepth = searchDepth;
        this.rootIsBlack = rootIsBlack;
    }

    public static BitMove getBestMove(BitBoard bitboard, int depth) {
        return new Minimax(depth, !bitboard.getIsWhiteToMove()).search(bitboard);
    }

    private BitMove search(BitBoard bitboard) {
        int maxDepth = searchDepth;
        int bestValue = 1000000;
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
                bestValue = result.value;
            }
        }
        // System.out.println("prunings: " + prunings);
        //System.out.println("num of nodes: " + nodesInMaxDepth);
        //System.out.println("nodes checked: " + nodesChecked);
        System.out.println("time: " + timeElapsed);
        System.out.println("Best value: " + bestValue);
        return bestMove;
    }

    private void getNumOfNodes(BitBoard board, int depth) {
        nodesInMaxDepth += board.getNextStates().size();
        if (depth == 1) return;
        for (BitBoard bitBoard : board.getNextStates()) {
            getNumOfNodes(board, depth - 1);
        }
    }

    private MinimaxResult minimax(BitBoard board, int depth, boolean isMaximizingPlayer, int alpha, int beta, BoardStateTracker boardStateTracker, TranspositionTable transpositionTable) {
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
            int value = BitBoardEvaluate.evaluate(board, rootIsBlack);
            // Prefer the quickest mate (and the slowest loss): a mate found with more depth
            // still to go is closer to the root. Without this, mate-in-1 and mate-in-3 tie.
            if (value >= BitBoardEvaluate.MATE) {
                value += depth;
            } else if (value <= -BitBoardEvaluate.MATE) {
                value -= depth;
            }
            return new MinimaxResult(board.lastMove, value);
        }

        if (boardStateTracker.isThreefoldRepetition()) {
            System.out.println("repetition!!! this is a stalemate!");
            boardStateTracker.removeLastBoardState();
            return new MinimaxResult(board.lastMove, -1111111);
        }

        BitMove bestMove = board.getRandomPossibleMove();
        boolean lastDepth = depth == searchDepth; // the root: its children are the candidate moves
        if (lastDepth) {
            bestMoves.clear();
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

    private boolean alreadyAdded(BitMove move) {
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

}
