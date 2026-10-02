package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitMove;
import ai.eval.Evaluator;

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
 *
 * <p>Memory (Phase 4b): a position drops its children once they have been searched, so only the
 * line being searched and the root's moves stay in memory, not the whole tree.
 *
 * <p>Evaluation (Phase 5): the leaves are scored by an {@link Evaluator} the caller chooses; the
 * overloads without one use {@link BitBoardEvaluate#DEFAULT}. When the depth runs out the search
 * does not stop in the middle of an exchange: a quiescence search plays on the captures and
 * promotions (and every reply to a check) until the position is quiet, so a leaf never counts a
 * piece that is about to be taken back.
 */
public class Minimax {
    private static final Random random = new Random();

    /** How many nodes pass between two looks at the stop signal. */
    private static final int STOP_CHECK_INTERVAL = 256;

    /** How many plies the quiescence search may add beyond the search depth. */
    static final int MAX_QUIESCENCE_DEPTH = 8;

    /** Thrown to unwind an abandoned depth; carries no stack trace. */
    private static final class Abandoned extends RuntimeException {
        Abandoned() {
            super(null, null, false, false);
        }
    }

    private static final Abandoned ABANDONED = new Abandoned();

    private final int searchDepth;
    private final Evaluator evaluator;
    private final BooleanSupplier stop;
    /** True when the side choosing the move (the side to move at the root) is Black. */
    private final boolean rootIsBlack;
    private final ArrayList<BitMove> bestMoves = new ArrayList<>();
    private int nodesChecked = 0;

    private Minimax(int searchDepth, Evaluator evaluator, boolean rootIsBlack, BooleanSupplier stop) {
        this.searchDepth = searchDepth;
        this.evaluator = evaluator;
        this.rootIsBlack = rootIsBlack;
        this.stop = stop;
    }

    /** The best move at exactly {@code depth}, however long it takes. */
    public static BitMove getBestMove(BitBoard bitboard, int depth) {
        return getBestMove(bitboard, depth, () -> false);
    }

    /** {@link #getBestMove(BitBoard, int, Evaluator, BooleanSupplier)} with the default evaluation. */
    public static BitMove getBestMove(BitBoard bitboard, int maxDepth, BooleanSupplier stop) {
        return getBestMove(bitboard, maxDepth, BitBoardEvaluate.DEFAULT, stop);
    }

    /**
     * The best move of the deepest depth (up to {@code maxDepth}) finished before {@code stop}
     * returned true. With a stop that never fires this is the same move as a single search at
     * {@code maxDepth}: every depth is an independent search.
     */
    public static BitMove getBestMove(BitBoard bitboard, int maxDepth, Evaluator evaluator, BooleanSupplier stop) {
        boolean rootIsBlack = !bitboard.getIsWhiteToMove();
        BitMove best = new Minimax(1, evaluator, rootIsBlack, () -> false).search(bitboard);
        for (int depth = 2; depth <= maxDepth && !stop.getAsBoolean(); depth++) {
            try {
                best = new Minimax(depth, evaluator, rootIsBlack, stop).search(bitboard);
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
        if (depth == 0) {
            return new MinimaxResult(board.lastMove, quiescence(board, 0, isMaximizingPlayer, alpha, beta));
        }
        if (board.getStatus() != 1) {
            int value = evaluator.evaluate(board, rootIsBlack);
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
        if (!lastDepth) {
            board.releaseNextStates(); // searched: let its subtree go; the root keeps its moves between depths
        }
//
//        // שמירת התוצאה בטבלת טרנספוזיציות
//        transpositionTable.put(zobristHash, depth, bestValue, bestMove);

        return new MinimaxResult(bestMove, bestValue);
    }

    /**
     * Plays on the captures and promotions of a position past the search depth until it is quiet.
     * The side to move may also "stand pat" (keep the static evaluation) instead of capturing, since
     * it is never forced to take. In check at the first ply it must answer, so every move is searched
     * and there is no standing pat; deeper checks are scored as they stand, which keeps the search
     * small. Captures that lose material are not tried ({@link BitBoard#getNoisyNextStates()}).
     */
    private int quiescence(BitBoard board, int ply, boolean isMaximizingPlayer, int alpha, int beta) {
        if (++nodesChecked % STOP_CHECK_INTERVAL == 0 && stop.getAsBoolean()) {
            throw ABANDONED;
        }
        // only the first ply answers a check with every move; deeper, a check is scored as it stands
        boolean inCheck = ply == 0 && board.isSideToMoveInCheck();
        if (ply >= MAX_QUIESCENCE_DEPTH || inCheck && board.getStatus() != 1) { // mated, or a 50-move draw
            return mateDistance(evaluator.evaluate(board, rootIsBlack), ply);
        }
        int best;
        if (inCheck) {
            best = isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        } else {
            best = evaluator.evaluate(board, rootIsBlack); // stand pat
            if (Math.abs(best) >= BitBoardEvaluate.MATE || best == 0 && board.getStatus() != 1) {
                return mateDistance(best, ply); // over already: mated or drawn
            }
            if (isMaximizingPlayer ? best >= beta : best <= alpha) {
                return best;
            }
        }
        if (isMaximizingPlayer) {
            alpha = Math.max(alpha, best);
        } else {
            beta = Math.min(beta, best);
        }
        for (BitBoard child : inCheck ? board.getSortedNextStates() : board.getNoisyNextStates()) {
            int value = quiescence(child, ply + 1, !isMaximizingPlayer, alpha, beta);
            if (isMaximizingPlayer) {
                best = Math.max(best, value);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, value);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha) {
                break;
            }
        }
        board.releaseNextStates();
        return best;
    }

    /** A mate {@code ply} plies past the search depth is that much further away than one at it. */
    private static int mateDistance(int value, int ply) {
        if (value >= BitBoardEvaluate.MATE) {
            return value - ply;
        }
        if (value <= -BitBoardEvaluate.MATE) {
            return value + ply;
        }
        return value;
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
