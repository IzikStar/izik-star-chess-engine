package ai.eval;

import ai.board.PieceBoard;
import ai.piece.Grid;
import ai.variant.Variant;

/**
 * How a position becomes the inputs of the network (docs/phase-7-research.md, decided 2026-10-06):
 * one input per (side, piece type, square) that is on or off, seen from the player to move.
 * <ul>
 *   <li>Inputs are numbered {@code side * T * S + type * S + square}: {@code T} piece types in the
 *       order of {@link Variant#pieces()}, {@code S} squares; side 0 is the player to move, side 1
 *       the opponent. Chess: 2 x 6 x 64 = 768.</li>
 *   <li>The square is the {@link Grid}'s id (a8 = 0 ... h1 = 63) for player 0; for player 1 the
 *       board is mirrored top to bottom (e8 becomes e1), so both players see their own back rank
 *       at the bottom and a pattern learned for one side serves the other.</li>
 * </ul>
 * A position of 30 pieces has 30 inputs on and the rest off. {@code tools/net/encode.py} writes the
 * same numbering in Python; the two must agree, or a trained network scores other positions than
 * it was trained on.
 */
public final class NetFeatures {

    private NetFeatures() {}

    /** How many inputs {@code variant}'s positions have: 2 x piece types x squares. */
    public static int inputs(Variant variant) {
        return 2 * variant.pieces().size() * variant.grid().squares();
    }

    /** The input for a piece of {@code type} on {@code square} (already seen from the viewer) of side 0 (own) or 1. */
    public static int index(Variant variant, int side, int type, int square) {
        int squares = variant.grid().squares();
        return (side * variant.pieces().size() + type) * squares + square;
    }

    /** {@code square} as {@code player} sees it: as it is for player 0, mirrored top to bottom for player 1. */
    public static int fromSide(Grid grid, int player, int square) {
        return player == 0 ? square : grid.square(grid.height() - 1 - grid.row(square), grid.col(square));
    }

    /**
     * The inputs that are on in {@code board}, seen from {@code viewer} (0 or 1), in ascending
     * order: first the viewer's pieces type by type, then the opponent's.
     */
    public static int[] active(PieceBoard board, int viewer) {
        Variant variant = board.variant();
        Grid grid = variant.grid();
        int types = variant.pieces().size();
        int[] out = new int[grid.squares()];
        int n = 0;
        for (int side = 0; side < 2; side++) {
            int player = side == 0 ? viewer : 1 - viewer;
            for (int type = 0; type < types; type++) {
                long bits = board.pieces(player, type);
                // the mirrored squares of a type come out in another order; sort each type's run below
                int start = n;
                while (bits != 0) {
                    int square = Long.numberOfTrailingZeros(bits);
                    bits &= bits - 1;
                    out[n++] = index(variant, side, type, fromSide(grid, viewer, square));
                }
                if (viewer == 1) {
                    java.util.Arrays.sort(out, start, n);
                }
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** {@link #active(PieceBoard, int)} from the side to move, the view the network scores from. */
    public static int[] active(PieceBoard board) {
        return active(board, board.sideToMove());
    }
}
