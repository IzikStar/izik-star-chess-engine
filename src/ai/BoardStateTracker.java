package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.ZobristHashing;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class BoardStateTracker {
    private final Stack<Long> currentBranchStack; // סטאק של המצבים בענף הנוכחי
    private final Map<Long, Integer> allBoardStates; // מפה של כל המצבים שנבדקו

    public BoardStateTracker() {
        currentBranchStack = new Stack<>();
        allBoardStates = new HashMap<>();
    }

    // הוספת מצב חדש לסטאק ולמפה
    public void addBoardState(BitBoard board) {
        long hash = ZobristHashing.computeHash(board);
        currentBranchStack.push(hash);

        // עדכון המצב במפה
        allBoardStates.put(hash, allBoardStates.getOrDefault(hash, 0) + 1);
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

    // בדיקה אם המצב הנוכחי חזר 3 פעמים
    public boolean isThreefoldRepetition() {
        if (!currentBranchStack.isEmpty()) {
            long lastHash = currentBranchStack.peek();
            return allBoardStates.getOrDefault(lastHash, 0) >= 3;
        }
        return false;
    }
}
