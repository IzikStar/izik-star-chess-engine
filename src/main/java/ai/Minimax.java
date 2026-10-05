package ai;

import ai.BitBoard.BitBoardEvaluate;
import ai.board.Board;
import ai.board.Move;
import ai.eval.Evaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Alpha-beta search over a {@link Board}: it reads positions only through that interface, so it
 * plays any board that implements it (Phase 6). Each depth runs on its own instance, so no search state
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
 * overloads without one use {@link BitBoardEvaluate#CLASSIC}. When the depth runs out the search
 * does not stop in the middle of an exchange: a quiescence search plays on the captures and
 * promotions (and every reply to a check) until the position is quiet, so a leaf never counts a
 * piece that is about to be taken back.
 *
 * <p>Transposition table and move ordering (Phase 5b, docs/phase-5b-research.md): the depths of one
 * {@code getBestMove} call share a {@link TranspositionTable}, killer moves and a history table.
 * A position already searched deeply enough returns its stored result, and every position tries
 * first the move that was best there in the previous depth, then captures (most valuable victim
 * first), then the quiet moves that cut off the search at the same ply or elsewhere. Both only make
 * the search faster: at the same depth it finds the same value, but it may pick another move of
 * that value. All of it lives in the call, so searches on different threads share nothing.
 *
 * <p>Repetition: the search is given the game's positions before the root, and any position that
 * already occurred (in the game or earlier on the line being searched) is scored as a draw, 0. So a
 * side that is ahead steers away from repeating and a side that is behind may go for it. Before,
 * the search saw no game history and only a third occurrence inside its own tree counted, so a
 * winning engine happily walked into a threefold repetition.
 */
public class Minimax {

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
    /** The player choosing the move: the side to move at the root. */
    private final int rootPlayer;
    /** How far below the best score a root move may be and still be picked (0 = only the best). */
    private final int variety;
    private final Random random;
    /** The root's moves that scored within {@link #variety} of the best, with their scores. */
    private final boolean quiescence;
    private final ArrayList<Integer> rootMoves = new ArrayList<>();
    private final ArrayList<Integer> rootValues = new ArrayList<>();
    /** Shared by the depths of one {@code getBestMove} call; null searches as before Phase 5b. */
    private final SearchState state;
    /** {@link Board#repetitionKey()} of the game's positions before the root. */
    private final long[] gameHistory;
    private int nodesChecked = 0;
    /** The root's score after {@link #search}, from the root side's point of view. */
    private int rootValue;

    /**
     * How a search picks its move. {@code variety}: how far below the best score (pawn = 100) a root
     * move may be and still be played, picked with {@code random}; 0 always plays the best.
     * {@code quiescence}: play on through captures past the depth (off only to compare with the
     * search as it was before Phase 5). {@code transpositionTable} and {@code moveOrdering}: the
     * Phase 5b speedups (off only to compare with the search as it was before).
     */
    public record Options(int variety, Random random, boolean quiescence, boolean transpositionTable,
                          boolean moveOrdering) {
        public static final Options DEFAULT = new Options(0, null, true);

        public Options {
            if (variety < 0 || variety > 0 && random == null) {
                throw new IllegalArgumentException("variety needs a non-negative margin and a Random");
            }
        }

        /** With the Phase 5b transposition table and move ordering. */
        public Options(int variety, Random random, boolean quiescence) {
            this(variety, random, quiescence, true, true);
        }

        /** The same options with the Phase 5b speedups on or off. */
        public Options withSpeedups(boolean on) {
            return new Options(variety, random, quiescence, on, on);
        }
    }

    /** The searched-node count of the last finished depth on this thread, for benchmarks. */
    private static final ThreadLocal<long[]> LAST_NODES = ThreadLocal.withInitial(() -> new long[1]);

    /** Nodes (main search and quiescence) the last {@code getBestMove} call on this thread visited. */
    public static long lastNodeCount() {
        return LAST_NODES.get()[0];
    }

    private Minimax(int searchDepth, Evaluator evaluator, Options options, int rootPlayer,
                    BooleanSupplier stop, SearchState state, long[] gameHistory) {
        this.state = state;
        this.gameHistory = gameHistory;
        this.searchDepth = searchDepth;
        this.evaluator = evaluator;
        this.variety = options.variety();
        this.random = options.random();
        this.quiescence = options.quiescence();
        this.rootPlayer = rootPlayer;
        this.stop = stop;
    }

    /** The best move at exactly {@code depth}, however long it takes. */
    public static int getBestMove(Board root, int depth) {
        return getBestMove(root, depth, () -> false);
    }

    /** {@link #getBestMove(Board, int, Evaluator, BooleanSupplier)} with the default evaluation. */
    public static int getBestMove(Board root, int maxDepth, BooleanSupplier stop) {
        return getBestMove(root, maxDepth, BitBoardEvaluate.CLASSIC, stop);
    }

    /**
     * The best move of the deepest depth (up to {@code maxDepth}) finished before {@code stop}
     * returned true. With a stop that never fires this is the same move as a single search at
     * {@code maxDepth}: every depth is an independent search.
     */
    public static int getBestMove(Board root, int maxDepth, Evaluator evaluator, BooleanSupplier stop) {
        return getBestMove(root, maxDepth, evaluator, Options.DEFAULT, stop);
    }

    /**
     * Like {@link #getBestMove(Board, int, Evaluator, BooleanSupplier)}, with {@link Options}.
     * With a variety margin the move is not always the same: any root move scoring within it of the
     * best may be played. A seeded {@code Random} makes the choice repeatable, and a forced mate is
     * never traded for variety.
     */
    public static int getBestMove(Board root, int maxDepth, Evaluator evaluator, Options options,
                                      BooleanSupplier stop) {
        return getBestMove(root, maxDepth, evaluator, options, NO_HISTORY, stop);
    }

    /** No game positions before the root. */
    public static final long[] NO_HISTORY = new long[0];

    /**
     * Like {@link #getBestMove(Board, int, Evaluator, Options, BooleanSupplier)}, knowing the game's
     * positions before the root ({@link Board#repetitionKey()}, any order): a move back into one
     * of them is scored as a draw.
     */
    public static int getBestMove(Board root, int maxDepth, Evaluator evaluator, Options options,
                                      long[] gameHistory, BooleanSupplier stop) {
        int rootPlayer = root.sideToMove();
        SearchState state = options.transpositionTable() || options.moveOrdering()
                ? new SearchState(maxDepth, options) : null;
        Minimax first = new Minimax(1, evaluator, options, rootPlayer, () -> false, state, gameHistory);
        int best = first.search(root);
        long nodes = first.nodesChecked;
        for (int depth = 2; depth <= maxDepth && !stop.getAsBoolean(); depth++) {
            Minimax next = new Minimax(depth, evaluator, options, rootPlayer, stop, state, gameHistory);
            try {
                best = next.search(root);
            } catch (Abandoned e) {
                break;
            } finally {
                nodes += next.nodesChecked;
            }
        }
        LAST_NODES.get()[0] = nodes;
        return best;
    }

    /**
     * The score of the best move at exactly {@code depth}, deepening as {@code getBestMove} does.
     * For tests: the Phase 5b speedups must not change it.
     */
    static int searchValue(Board root, int depth, Evaluator evaluator, Options options) {
        int rootPlayer = root.sideToMove();
        SearchState state = options.transpositionTable() || options.moveOrdering()
                ? new SearchState(depth, options) : null;
        int value = 0;
        for (int d = 1; d <= depth; d++) {
            Minimax search = new Minimax(d, evaluator, options, rootPlayer, () -> false, state, NO_HISTORY);
            search.search(root);
            value = search.rootValue;
        }
        return value;
    }

    private int search(Board root) {
        BoardStateTracker boardStateTracker = new BoardStateTracker(gameHistory);
        MinimaxResult result = minimax(root, searchDepth, true, Integer.MIN_VALUE, Integer.MAX_VALUE,
                boardStateTracker);
        rootValue = result.value;
        if (variety == 0 || Math.abs(result.value) >= Evaluator.MATE) {
            return result.move;
        }
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < rootMoves.size(); i++) {
            if (rootValues.get(i) >= result.value - variety) {
                candidates.add(rootMoves.get(i));
            }
        }
        return candidates.isEmpty() ? result.move : candidates.get(random.nextInt(candidates.size()));
    }

    private MinimaxResult minimax(Board board, int depth, boolean isMaximizingPlayer, int alpha, int beta,
                                  BoardStateTracker boardStateTracker) {
        if (++nodesChecked % STOP_CHECK_INTERVAL == 0 && stop.getAsBoolean()) {
            throw ABANDONED;
        }
        boolean lastDepth = depth == searchDepth; // the root: its children are the candidate moves
        // A position already on the path (in the game or on this line) is a draw: the side that
        // wants it can repeat again. Checked before the table, whose entries don't know the path.
        long repetitionHash = board.repetitionKey();
        if (!lastDepth && boardStateTracker.contains(repetitionHash)) {
            return new MinimaxResult(board.lastMove(), DRAW);
        }
        TranspositionTable table = state == null ? null : state.table;
        long key = 0;
        int hashMove = 0;
        if (table != null && depth > 0) {
            key = board.searchKey();
            int slot = table.find(key);
            if (slot >= 0) {
                hashMove = table.move(slot);
                if (!lastDepth && table.depth(slot) >= depth) {
                    int value = fromTable(table.value(slot), depth);
                    int bound = table.bound(slot);
                    if (bound == TranspositionTable.EXACT
                            || bound == TranspositionTable.LOWER && value >= beta
                            || bound == TranspositionTable.UPPER && value <= alpha) {
                        return new MinimaxResult(Move.NONE, value); // only the root's move is ever read
                    }
                }
            }
        }
        if (depth == 0 && quiescence) {
            return new MinimaxResult(board.lastMove(), quiescence(board, 0, isMaximizingPlayer, alpha, beta));
        }
        if (depth == 0 || board.isOver()) {
            int value = evaluator.evaluate(board, rootPlayer);
            // Prefer the quickest mate (and the slowest loss): a mate found with more depth
            // still to go is closer to the root. Without this, mate-in-1 and mate-in-3 tie.
            if (value >= Evaluator.MATE) {
                value += depth;
            } else if (value <= -Evaluator.MATE) {
                value -= depth;
            }
            if (table != null && depth > 0) {
                table.put(key, depth, toTable(value, depth), TranspositionTable.EXACT, 0);
            }
            return new MinimaxResult(board.lastMove(), value);
        }

        boardStateTracker.addBoardState(repetitionHash); // leaves return above, so only nodes that search on are on the path

        List<Board> children = board.orderedChildren(); // sorted once, best-ordered first
        int ply = searchDepth - depth;
        if (state != null) {
            state.order(children, board, hashMove, ply);
        }
        int bestMove = children.getFirst().lastMove();
        int alphaBefore = alpha;
        int betaBefore = beta;
        int bestValue;

        if (isMaximizingPlayer) {
            bestValue = Integer.MIN_VALUE;
            for (Board child : children) {
                MinimaxResult result = minimax(child, depth - 1, false, alpha, beta, boardStateTracker);

                if (result.value > bestValue) {
                    bestMove = child.lastMove();
                    bestValue = result.value;
                }
                if (lastDepth && variety > 0) {
                    rootMoves.add(child.lastMove());
                    rootValues.add(result.value);
                }
                // at the root, keep the window open by `variety` so near-best moves get exact scores
                alpha = Math.max(alpha, lastDepth && variety > 0 && bestValue > Integer.MIN_VALUE + variety
                        ? bestValue - variety - 1 : bestValue);
                if (beta <= alpha) {
                    cutoff(board, child, depth, ply);
                    break; // אלפא-בטא גיזום
                }
            }
        } else {
            bestValue = Integer.MAX_VALUE;
            for (Board child : children) {
                MinimaxResult result = minimax(child, depth - 1, true, alpha, beta, boardStateTracker);

                if (result.value < bestValue) {
                    bestValue = result.value;
                    bestMove = child.lastMove();
                }
                beta = Math.min(beta, bestValue);
                if (beta <= alpha) {
                    cutoff(board, child, depth, ply);
                    break; // אלפא-בטא גיזום
                }
            }
        }

        boardStateTracker.removeLastBoardState();
        if (!lastDepth) {
            board.releaseChildren(); // searched: let its subtree go; the root keeps its moves between depths
        }
        if (table != null) {
            int bound = bestValue <= alphaBefore ? TranspositionTable.UPPER
                    : bestValue >= betaBefore ? TranspositionTable.LOWER : TranspositionTable.EXACT;
            if (lastDepth) {
                bound = TranspositionTable.EXACT; // the root searches with the full window
            }
            // a fail-low has no best move worth remembering; keep the one an earlier visit found
            int move = bound == TranspositionTable.UPPER ? Move.NONE : bestMove;
            table.put(key, depth, toTable(bestValue, depth), bound, move);
        }

        return new MinimaxResult(bestMove, bestValue);
    }

    /** Remembers a quiet move that cut the search off, as a killer at its ply and in the history. */
    private void cutoff(Board parent, Board child, int depth, int ply) {
        if (state != null && state.moveOrdering && child.captureScore(parent) < 0) {
            state.rememberCutoff(child.lastMove(), parent.sideToMove(), depth, ply);
        }
    }

    /**
     * Mate scores count the plies to the mate from the root (see the leaf above); the table stores
     * them counted from the position itself, so a position reached at another ply reads them right.
     * The search's mate value at {@code depth} to go is MATE + depth - plies to the mate.
     */
    private static int toTable(int value, int depth) {
        if (value >= MATE_THRESHOLD) {
            return value - depth;
        }
        if (value <= -MATE_THRESHOLD) {
            return value + depth;
        }
        return value;
    }

    private static int fromTable(int value, int depth) {
        if (value >= MATE_THRESHOLD) {
            return value + depth;
        }
        if (value <= -MATE_THRESHOLD) {
            return value - depth;
        }
        return value;
    }

    /** A repetition's score: a draw, worth the same to both sides. */
    private static final int DRAW = 0;

    /** Any score this far from MATE is a mate score, wherever in the search it was found. */
    private static final int MATE_THRESHOLD = Evaluator.MATE - 1000;

    /**
     * What the depths of one {@code getBestMove} call share (Phase 5b): the transposition table, two
     * killer moves per ply and the history of quiet moves that cut the search off.
     */
    private static final class SearchState {
        final TranspositionTable table;
        final boolean moveOrdering;
        final int[][] killers;
        /**
         * [side to move][from][to]: how much a quiet move has cut off, weighted by depth squared.
         * Two players on 64 squares, as long as chess is the only board.
         */
        final int[][][] history = new int[2][64][64];

        SearchState(int maxDepth, Options options) {
            table = options.transpositionTable() ? new TranspositionTable(tableBits(maxDepth)) : null;
            moveOrdering = options.moveOrdering();
            killers = new int[maxDepth + 1][2];
        }

        /**
         * 2^16 slots (1 MB) for shallow searches up to 2^20 (16 MB) from depth 5: enough for the
         * positions a search visits, without allocating 16 MB for every quick depth-3 arena move.
         */
        static int tableBits(int maxDepth) {
            return Math.min(20, 14 + maxDepth);
        }

        void rememberCutoff(int move, int side, int depth, int ply) {
            if (killers[ply][0] != move) {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = move;
            }
            int[] row = history[side][Move.from(move)];
            row[Move.to(move)] += depth * depth;
            if (row[Move.to(move)] > HISTORY_MAX) {
                for (int[][] player : history) {
                    for (int[] r : player) {
                        for (int i = 0; i < r.length; i++) {
                            r[i] /= 2;
                        }
                    }
                }
            }
        }

        private static final int HISTORY_MAX = 1 << 20;

        /**
         * Orders the children: the table's move, captures by MVV-LVA, the two killers of this ply,
         * then quiet moves by history. Ties keep the move generator's order (checks first).
         */
        void order(List<Board> children, Board parent, int hashMove, int ply) {
            int n = children.size();
            if (!moveOrdering) {
                if (hashMove != 0) {
                    for (int i = 0; i < n; i++) {
                        if (children.get(i).lastMove() == hashMove) {
                            children.addFirst(children.remove(i));
                            break;
                        }
                    }
                }
                return;
            }
            int side = parent.sideToMove();
            long[] keyed = new long[n];
            for (int i = 0; i < n; i++) {
                Board child = children.get(i);
                int code = child.lastMove();
                long score;
                int capture;
                if (code == hashMove) {
                    score = 4L << 40;
                } else if ((capture = child.captureScore(parent)) >= 0) {
                    score = (3L << 40) + capture;
                } else if (code == killers[ply][0]) {
                    score = (2L << 40) + 1;
                } else if (code == killers[ply][1]) {
                    score = 2L << 40;
                } else {
                    score = history[side][Move.from(code)][Move.to(code)];
                }
                keyed[i] = (-score) << 8 | i; // descending score, then generator order
            }
            Arrays.sort(keyed);
            ArrayList<Board> ordered = new ArrayList<>(n);
            for (long k : keyed) {
                ordered.add(children.get((int) (k & 0xFF)));
            }
            children.clear();
            children.addAll(ordered);
        }
    }

    /**
     * Plays on the captures and promotions of a position past the search depth until it is quiet.
     * The side to move may also "stand pat" (keep the static evaluation) instead of capturing, since
     * it is never forced to take. In check at the first ply it must answer, so every move is searched
     * and there is no standing pat; deeper checks are scored as they stand, which keeps the search
     * small. Captures that lose material are not tried ({@link Board#noisyChildren()}).
     */
    private int quiescence(Board board, int ply, boolean isMaximizingPlayer, int alpha, int beta) {
        if (++nodesChecked % STOP_CHECK_INTERVAL == 0 && stop.getAsBoolean()) {
            throw ABANDONED;
        }
        // only the first ply answers a check with every move; deeper, a check is scored as it stands
        boolean inCheck = ply == 0 && board.inCheck();
        if (ply >= MAX_QUIESCENCE_DEPTH || inCheck && board.isOver()) { // mated, or a 50-move draw
            return mateDistance(evaluator.evaluate(board, rootPlayer), ply);
        }
        int best;
        if (inCheck) {
            best = isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        } else {
            best = evaluator.evaluate(board, rootPlayer); // stand pat
            if (Math.abs(best) >= Evaluator.MATE || best == 0 && board.isOver()) {
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
        for (Board child : inCheck ? board.orderedChildren() : board.noisyChildren()) {
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
        board.releaseChildren();
        return best;
    }

    /** A mate {@code ply} plies past the search depth is that much further away than one at it. */
    private static int mateDistance(int value, int ply) {
        if (value >= Evaluator.MATE) {
            return value - ply;
        }
        if (value <= -Evaluator.MATE) {
            return value + ply;
        }
        return value;
    }

    private static class MinimaxResult {
        int move;
        int value;

        MinimaxResult(int move, int value) {
            this.move = move;
            this.value = value;
        }
    }

}
