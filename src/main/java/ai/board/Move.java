package ai.board;

/**
 * A move as one {@code int}: from-square, to-square and the piece a promotion turns into. Squares are
 * the board's own square ids (chess: a8 = 0 ... h1 = 63, see {@code rules.Square}). The search's
 * transposition table, killer moves and history store moves in this form. {@link #NONE} (0) is never
 * a move, since a move's from- and to-square differ.
 *
 * <p>Layout: bits 0-7 from, 8-15 to, 16-20 the promotion piece's letter as 1-26 for a-z (0 when
 * none): 21 bits, which the transposition table stores whole. Eight bits per square leave room for
 * boards of up to 256 squares.
 */
public final class Move {

    /** Not a move: what a search returns when the position has none. */
    public static final int NONE = 0;

    private Move() {}

    public static int of(int from, int to) {
        return of(from, to, (char) 0);
    }

    /** {@code promotion}: the lower-case letter of the piece a promotion turns into, or 0. */
    public static int of(int from, int to, char promotion) {
        int letter = promotion == 0 ? 0 : Character.toLowerCase(promotion) - 'a' + 1;
        if (letter < 0 || letter > 26) {
            throw new IllegalArgumentException("a promotion is a letter a-z: " + promotion);
        }
        return from | to << 8 | letter << 16;
    }

    public static int from(int move) {
        return move & 0xFF;
    }

    public static int to(int move) {
        return move >>> 8 & 0xFF;
    }

    /** The lower-case letter of the piece the move promotes to, or 0. */
    public static char promotion(int move) {
        int letter = move >>> 16 & 0x1F;
        return letter == 0 ? 0 : (char) ('a' + letter - 1);
    }
}
