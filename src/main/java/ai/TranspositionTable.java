package ai;

/**
 * The search's transposition table (Phase 5b, docs/phase-5b-research.md §3): what an earlier visit
 * learned about a position, so the search does not search the same position twice and tries the
 * move that was best there first.
 *
 * <p>A fixed number of slots in two plain arrays (a key and a packed entry per slot), so it never
 * grows and allocates nothing while the search runs. Slots come in pairs: the first keeps the
 * deeper result (a deeper search is worth more), the second always takes the newest. One table
 * belongs to one search; it is not thread-safe and is never shared between threads.
 */
public final class TranspositionTable {

    /** The stored value is exact. */
    public static final int EXACT = 1;
    /** The value is a lower bound: the search failed high (true value >= value). */
    public static final int LOWER = 2;
    /** The value is an upper bound: the search failed low (true value <= value). */
    public static final int UPPER = 3;

    private final long[] keys;
    /** value (32 bits) | depth (8) | bound (2) | move code (22). */
    private final long[] entries;
    private final int mask;

    /** A table of {@code 2^bits} slots, 16 bytes each. */
    public TranspositionTable(int bits) {
        if (bits < 1 || bits > 26) {
            throw new IllegalArgumentException("bits must be between 1 and 26");
        }
        keys = new long[1 << bits];
        entries = new long[1 << bits];
        mask = (1 << bits) - 2; // the even slot of a pair
    }

    /** The slot holding {@code key}, or -1. */
    public int find(long key) {
        int slot = (int) key & mask;
        if (keys[slot] == key && entries[slot] != 0) {
            return slot;
        }
        if (keys[slot + 1] == key && entries[slot + 1] != 0) {
            return slot + 1;
        }
        return -1;
    }

    public int value(int slot) {
        return (int) (entries[slot] >> 32);
    }

    public int depth(int slot) {
        return (int) (entries[slot] >>> 24) & 0xFF;
    }

    public int bound(int slot) {
        return (int) (entries[slot] >>> 22) & 0x3;
    }

    public int move(int slot) {
        return (int) entries[slot] & 0x3FFFFF;
    }

    /** Stores a result; {@code move} 0 means none. */
    public void put(long key, int depth, int value, int bound, int move) {
        long entry = (long) value << 32 | (long) (depth & 0xFF) << 24 | (long) (bound & 0x3) << 22 | (move & 0x3FFFFF);
        int slot = (int) key & mask;
        if (keys[slot] == key || entries[slot] == 0 || depth >= depth(slot)) {
            if (keys[slot] != key && entries[slot] != 0) {
                keys[slot + 1] = keys[slot]; // the deeper entry it displaces moves to the newest slot
                entries[slot + 1] = entries[slot];
            }
            if (keys[slot] == key && move == 0) {
                entry |= move(slot); // keep the move an earlier visit found
            }
            keys[slot] = key;
            entries[slot] = entry;
        } else {
            if (keys[slot + 1] == key && move == 0) {
                entry |= move(slot + 1);
            }
            keys[slot + 1] = key;
            entries[slot + 1] = entry;
        }
    }
}
