package ai;

import ai.BitBoard.BitMove;

import java.util.HashMap;
import java.util.Map;

public class TranspositionTable {
    private final Map<Long, TranspositionTableEntry> table;

    public TranspositionTable() {
        table = new HashMap<>();
    }

    public TranspositionTableEntry get(long zobristHash) {
        return table.get(zobristHash);
    }

    public void put(long zobristHash, int depth, int value, BitMove bestMove) {
        table.put(zobristHash, new TranspositionTableEntry(depth, value, bestMove));
    }

    public static class TranspositionTableEntry {
        public final int depth;
        public final int value;
        public final BitMove bestMove;

        public TranspositionTableEntry(int depth, int value, BitMove bestMove) {
            this.depth = depth;
            this.value = value;
            this.bestMove = bestMove;
        }
    }
}

