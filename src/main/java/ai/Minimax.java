package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitMove;

import java.util.ArrayList;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Alpha-beta search over the bitboard. Each depth runs on its own instance, so no search state
 * survives between searches, and the search always chooses a move for the side to move at the
 * root — it reads no UI settings (Phase 3; the old version inferred "am I at the root" from
 * {@code ChoosePlayFormat}, which broke whenever those flags were flipped around an engine call).
 *
 * <p>Iterative deepening (Phase 4): depths 1, 2, … up to the requested depth, each a complete
 * search of its own. A {@code stop} signal (cancelled, or out of time) abandons the depth in
 * progress and the move of the deepest finished depth is played. Depth 1 always finishes.
 */
public class Minimax {
    private static final Random random = new Random();

    /** How many nodes pass between two looks at the stop signal. */
    private static final int STOP_CHECK_INTERVAL = 256;

    /** Thrown to unwind an abandoned depth; carries no stack trace. */
    private static final class Abandoned extends RuntimeException {
        Abandoned() {
            super(null, null, false, false);
        }
    }

    private static final Abandoned ABANDONED = new Abandoned();

    private final int searchDepth;
    private final BooleanSupplier stop;
    /** True when the side choosing the move (the side to move at the root) is Black. */
    private final boolean rootIsBlack;
    private final ArrayList<BitMove> bestMoves = new ArrayList<>();
    private int nodesChecked = 0;

    private Minimax(int searchDepth, boolean rootIsBlack, BooleanSupplier stop) {
        this.searchDepth = searchDepth;
        this.rootIsBlack = rootIsBlack;
        this.stop = stop;
    }

    /** The best move at exactly {@code depth}, however long it takes. */
    public static BitMove getBestMove(BitBoard bitboard, int depth) {
        return getBestMove(bitboard, depth, () -> false);
    }

    /**
     * The best move of the deepest depth (up to {@code maxDepth}) finished before {@code stop}
     * returned true. With a stop that never fires this is the same move as a single search at
     * {@code maxDepth}: every depth is an independent search.
     */
    public static BitMove getBestMove(BitBoard bitboard, int maxDepth, BooleanSupplier stop) {
        boolean rootIsBlack = !bitboard.getIsWhiteToMove();
        BitMove best = new Minimax(1, rootIsBlack, () -> false).search(bitboard);
        for (int depth = 2; depth <= maxDepth && !stop.getAsBoolean(); depth++) {
            try {
                best = new Minimax(depth, rootIsBlack, stop).search(bitboard);
            } catch (Abandoned e) {
                break;
            }
        }
        return best;
    }

    private BitMove search(BitBoard bitboard) {
        BoardStateTracker boardStateTracker = new BoardStateTracker();
        TranspositionTable transpositionTable = new TranspositionTable();
        MinimaxResult result = minimax(bitboard, searchDepth, true, Integer.MIN_VALUE, Integer.MAX_VALUE,
                boardStateTracker, transpositionTable);
        if (!bestMoves.isEmpty()) {
            result.move = bestMoves.get(random.nextInt(bestMoves.size()));
        }
        return result.move;
    }

    private MinimaxResult minimax(BitBoard board, int depth, boolean isMaximizingPlayer, int alpha, int beta, BoardStateTracker boardStateTracker, TranspositionTable transpositionTable) {
        // long zobristHash = ZobristHashing.computeHash(board);

        // בדיקה אם המצב כבר קיים בטבלת טרנספוזיציות
//        TranspositionTable.TranspositionTableEntry entry = transpositionTable.get(zobristHash);
//        if (entry != null && entry.depth >= depth) {
//            System.out.println("this state has already been checked. returning saved result");
//            return new MinimaxResult(entry.bestMove, entry.value);
//        }

        if (++nodesChecked % STOP_CHECK_INTERVAL == 0 && stop.getAsBoolean()) {
            throw ABANDONED;
        }
        if (depth == 0 || board.getStatus() != 1) {
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

        boardStateTracker.addBoardState(board); // leaves return above, so only nodes that search on are hashed
        if (boardStateTracker.isThreefoldRepetition()) {
            // a draw, worth 0 to both sides (was -1111111 whoever was to move)
            boardStateTracker.removeLastBoardState();
            return new MinimaxResult(board.lastMove, 0);
        }

        ArrayList<BitBoard> children = board.getSortedNextStates(); // sorted once, best-ordered first
        BitMove bestMove = children.getFirst().lastMove;
        boolean lastDepth = depth == searchDepth; // the root: its children are the candidate moves
        if (lastDepth) {
            bestMoves.clear();
        }
        int bestValue;

        if (isMaximizingPlayer) {
            bestValue = Integer.MIN_VALUE;
            for (BitBoard state : children) {
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
            for (BitBoard state : children) {
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
