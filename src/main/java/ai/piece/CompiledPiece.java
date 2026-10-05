package ai.piece;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PieceType} turned into per-square tables for one player on one {@link Grid} (Phase 6 R2):
 * the move generator looks squares up instead of reading the atoms. Leaps become one bitboard per
 * square; a slide becomes, per square, the squares of its ray in order, walked until the first
 * piece in the way.
 *
 * <p>Squares the piece "captures on" include squares its own side stands on, as an attack set does;
 * the board removes those. Immutable, so one compiled piece serves every search on every thread.
 */
public final class CompiledPiece {

    private final PieceType type;
    private final Grid grid;
    private final int player;
    /** [first-move-only ? 1 : 0][square]. */
    private final long[][] leapMoves;
    private final long[][] leapCaptures;
    private final List<Ray> rays;

    /** One slide direction: per square the squares along it, nearest first, cut to the range. */
    private record Ray(int[][] squares, Atom.Mode mode, boolean firstMoveOnly) {}

    public CompiledPiece(PieceType type, Grid grid, int player) {
        if (player != 0 && player != 1) {
            throw new IllegalArgumentException("players are 0 and 1: " + player);
        }
        this.type = type;
        this.grid = grid;
        this.player = player;
        int n = grid.squares();
        leapMoves = new long[2][n];
        leapCaptures = new long[2][n];
        List<Ray> rays = new ArrayList<>();
        for (Atom atom : type.atoms()) {
            int first = atom.firstMoveOnly() ? 1 : 0;
            for (int[] offset : atom.offsets()) {
                int[] d = Grid.delta(player, offset[0], offset[1]);
                if (atom.kind() == Atom.Kind.LEAP) {
                    for (int sq = 0; sq < n; sq++) {
                        int row = grid.row(sq) + d[0];
                        int col = grid.col(sq) + d[1];
                        if (grid.contains(row, col)) {
                            long bit = 1L << grid.square(row, col);
                            if (atom.mode().moves()) {
                                leapMoves[first][sq] |= bit;
                            }
                            if (atom.mode().captures()) {
                                leapCaptures[first][sq] |= bit;
                            }
                        }
                    }
                } else {
                    int[][] squares = new int[n][];
                    for (int sq = 0; sq < n; sq++) {
                        List<Integer> line = new ArrayList<>();
                        int row = grid.row(sq) + d[0];
                        int col = grid.col(sq) + d[1];
                        while (grid.contains(row, col) && (atom.range() == 0 || line.size() < atom.range())) {
                            line.add(grid.square(row, col));
                            row += d[0];
                            col += d[1];
                        }
                        squares[sq] = line.stream().mapToInt(Integer::intValue).toArray();
                    }
                    rays.add(new Ray(squares, atom.mode(), atom.firstMoveOnly()));
                }
            }
        }
        this.rays = List.copyOf(rays);
    }

    public PieceType type() {
        return type;
    }

    public Grid grid() {
        return grid;
    }

    public int player() {
        return player;
    }

    /**
     * The empty squares the piece on {@code square} can move to without capturing.
     *
     * @param occupied  every occupied square, both sides
     * @param firstMove the piece has not moved yet, so first-move atoms count
     */
    public long quietTargets(int square, long occupied, boolean firstMove) {
        long targets = leapMoves[0][square] | (firstMove ? leapMoves[1][square] : 0);
        for (Ray ray : rays) {
            if (ray.mode.moves() && (firstMove || !ray.firstMoveOnly)) {
                for (int to : ray.squares[square]) {
                    long bit = 1L << to;
                    if ((occupied & bit) != 0) {
                        break;
                    }
                    targets |= bit;
                }
            }
        }
        return targets & ~occupied;
    }

    /**
     * The squares the piece on {@code square} attacks: where it would capture an enemy piece. Includes
     * squares its own side stands on and empty squares (it is an attack set: what a king may not walk
     * into).
     */
    public long captureTargets(int square, long occupied, boolean firstMove) {
        long targets = leapCaptures[0][square] | (firstMove ? leapCaptures[1][square] : 0);
        for (Ray ray : rays) {
            if (ray.mode.captures() && (firstMove || !ray.firstMoveOnly)) {
                for (int to : ray.squares[square]) {
                    long bit = 1L << to;
                    targets |= bit;
                    if ((occupied & bit) != 0) {
                        break;
                    }
                }
            }
        }
        return targets;
    }
}
