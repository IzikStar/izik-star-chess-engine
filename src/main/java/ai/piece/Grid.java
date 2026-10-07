package ai.piece;

/**
 * A rectangular board: {@code width} files and {@code height} rows, squares numbered row by row from
 * the top (chess: a8 = 0 ... h1 = 63, as in {@code rules.Square}). Row 0 is the far side for player 0,
 * so player 0 moves "forward" toward row 0 and player 1 toward the last row.
 *
 * <p>Bitboards are {@code long}s, so a grid has at most 64 squares for now; a bigger board needs a
 * wider square set (docs/phase-6-research.md §5).
 */
public record Grid(int width, int height) {

    public static final Grid CHESS = new Grid(8, 8);

    public Grid {
        if (width < 1 || height < 1 || width * height > 64) {
            throw new IllegalArgumentException("a grid has 1 to 64 squares: " + width + "x" + height);
        }
    }

    public int squares() {
        return width * height;
    }

    public int row(int square) {
        return square / width;
    }

    public int col(int square) {
        return square % width;
    }

    public boolean contains(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    /** The square's name: file letter from {@code a}, rank from 1 at the bottom (row 0 is the top). */
    public String name(int square) {
        return "" + (char) ('a' + col(square)) + (height - row(square));
    }

    public int square(int row, int col) {
        return row * width + col;
    }

    /** One step of {@code forward} rows and {@code right} files as {@code player} sees the board, as {row, col} deltas. */
    public static int[] delta(int player, int forward, int right) {
        // player 1 sees the board turned half a circle: their forward is down and their right is left
        return player == 0 ? new int[]{-forward, right} : new int[]{forward, -right};
    }
}
