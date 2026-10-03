package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.ZobristHashing;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * The positions on the way to the node being searched: the game's positions before the root
 * (seeded once), then the line from the root down. The search scores a position that is already on
 * this path as a draw: whoever wants the draw can repeat it again.
 */
public class BoardStateTracker {
    private final Stack<Long> currentBranchStack; // סטאק של המצבים בענף הנוכחי
    private final Map<Long, Integer> allBoardStates; // מפה של כל המצבים שנבדקו

    public BoardStateTracker() {
        currentBranchStack = new Stack<>();
        allBoardStates = new HashMap<>();
    }

    /** The game's positions before the root ({@link ZobristHashing#computeHash}); they are never removed. */
    public BoardStateTracker(long[] gameHistory) {
        this();
        for (long hash : gameHistory) {
            allBoardStates.merge(hash, 1, Integer::sum);
        }
    }

    // הוספת מצב חדש לסטאק ולמפה
    public void addBoardState(long hash) {
        currentBranchStack.push(hash);
        allBoardStates.merge(hash, 1, Integer::sum);
    }

    public void addBoardState(BitBoard board) {
        addBoardState(ZobristHashing.computeHash(board));
    }

    // הסרת מצב מהסטאק כשהענף מסתיים
    public void removeLastBoardState() {
        if (!currentBranchStack.isEmpty()) {
            long lastHash = currentBranchStack.pop();
            int count = allBoardStates.get(lastHash);
            if (count == 1) {
                allBoardStates.remove(lastHash);
            } else {
                allBoardStates.put(lastHash, count - 1);
            }
        }
    }

    /** True when {@code hash} already occurred in the game or on the line being searched. */
    public boolean contains(long hash) {
        return allBoardStates.containsKey(hash);
    }
}
