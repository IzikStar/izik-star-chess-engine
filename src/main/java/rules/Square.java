package rules;

/**
 * A board square as a 0..63 index in the engine's bit order:
 * {@code index = rank8Row * 8 + file}, where {@code rank8Row} is 0 for the 8th rank and 7 for
 * the 1st, and {@code file} is 0 (a) .. 7 (h). So a8 == 0 and h1 == 63 — the same order as
 * {@code 1L << index} inside the search's board ({@code ai.board}).
 *
 * <p>Part of the Phase 2 headless rules API: no Swing, no AWT, no {@code main.*}.
 */
public final class Square {
    private Square() {}

    /** Sentinel for "no square" (e.g. no en-passant target). */
    public static final int NONE = -1;

    public static int of(int file, int rank8Row) {
        return rank8Row * 8 + file;
    }

    /** 0 (a) .. 7 (h). */
    public static int file(int sq) {
        return sq & 7;
    }

    /** 0 for the 8th rank .. 7 for the 1st rank. */
    public static int rank8Row(int sq) {
        return sq >>> 3;
    }

    /** 1..8 — the chess rank number. */
    public static int rank(int sq) {
        return 8 - (sq >>> 3);
    }

    /** Parse algebraic square names like {@code "e4"}. */
    public static int fromName(String name) {
        if (name == null || name.length() != 2) {
            throw new IllegalArgumentException("bad square: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1' + 1;
        if (file < 0 || file > 7 || rank < 1 || rank > 8) {
            throw new IllegalArgumentException("bad square: " + name);
        }
        return of(file, 8 - rank);
    }

    /** {@code "e4"}, or {@code "-"} for {@link #NONE} / out of range. */
    public static String name(int sq) {
        if (sq < 0 || sq > 63) {
            return "-";
        }
        return "" + (char) ('a' + file(sq)) + (char) ('0' + rank(sq));
    }
}
