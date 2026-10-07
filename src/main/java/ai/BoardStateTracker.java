package ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * The positions on the way to the node being searched: the game's positions before the root
 * (seeded once), then the line from the root down. The search scores a position that is already on
 * this path as a draw: whoever wants the draw can repeat it again.
 */
public class BoardStateTracker {
    private final Deque<Long> branch = new ArrayDeque<>();
    private final Map<Long, Integer> counts = new HashMap<>();

    public BoardStateTracker() {}

    /** The game's positions before the root ({@link ai.board.Board#repetitionKey()}); they are never removed. */
    public BoardStateTracker(long[] gameHistory) {
        for (long hash : gameHistory) {
            counts.merge(hash, 1, Integer::sum);
        }
    }

    /** Pushes a position onto the line being searched. */
    public void addBoardState(long hash) {
        branch.push(hash);
        counts.merge(hash, 1, Integer::sum);
    }

    /** Pops the last pushed position when its branch is done. */
    public void removeLastBoardState() {
        if (!branch.isEmpty()) {
            counts.computeIfPresent(branch.pop(), (h, n) -> n == 1 ? null : n - 1);
        }
    }

    /** True when {@code hash} already occurred in the game or on the line being searched. */
    public boolean contains(long hash) {
        return counts.containsKey(hash);
    }
}
