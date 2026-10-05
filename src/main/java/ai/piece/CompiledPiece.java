package ai.piece;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PieceType} turned into per-square tables for one player on one {@link Grid} (Phase 6 R2):
 * the move generator looks squares up instead of reading the atoms. Leaps become one bitboard per
 * square. A slide becomes one bitboard per square of the squares along its direction (cut to its
 * range); the first piece in the way is the nearest set bit of that ray, so the squares up to it are
 * found with a few bit operations, without walking the ray.
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
    /**
     * Slides, per group [moves, captures] x [always, first move only], per square: the squares of
     * each ray that rises from the piece (its nearest blocker is the lowest bit), then of each ray
     * that falls (the highest bit). Empty rays are left out.
     */
    private final long[][][] risingRays = new long[4][][];
    private final long[][][] fallingRays = new long[4][][];
    /** [square]: every square the piece could ever capture on, on an empty board, first move or not. */
    private final long[] reach;
    /** [square]: the part of {@link #reach} its slides cover, the squares a piece in between could block. */
    private final long[] slideReach;
    /** Moves and captures go the same ways, so one pass over the rays serves both. */
    private final boolean sameMovesAndCaptures;

    private static int group(boolean captures, boolean firstMoveOnly) {
        return (captures ? 2 : 0) + (firstMoveOnly ? 1 : 0);
    }

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
        List<List<long[]>> rayLists = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        List<List<Boolean>> risingLists = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
                    long[] masks = new long[n];
                    for (int sq = 0; sq < n; sq++) {
                        int steps = 0;
                        int row = grid.row(sq) + d[0];
                        int col = grid.col(sq) + d[1];
                        while (grid.contains(row, col) && (atom.range() == 0 || steps < atom.range())) {
                            masks[sq] |= 1L << grid.square(row, col);
                            steps++;
                            row += d[0];
                            col += d[1];
                        }
                    }
                    boolean up = d[0] * grid.width() + d[1] > 0;
                    for (boolean captures : new boolean[]{false, true}) {
                        if (captures ? atom.mode().captures() : atom.mode().moves()) {
                            int g = group(captures, atom.firstMoveOnly());
                            rayLists.get(g).add(masks);
                            risingLists.get(g).add(up);
                        }
                    }
                }
            }
        }
        for (int g = 0; g < 4; g++) {
            risingRays[g] = new long[n][];
            fallingRays[g] = new long[n][];
            for (int sq = 0; sq < n; sq++) {
                List<Long> up = new ArrayList<>();
                List<Long> down = new ArrayList<>();
                for (int i = 0; i < rayLists.get(g).size(); i++) {
                    long ray = rayLists.get(g).get(i)[sq];
                    if (ray != 0) {
                        (risingLists.get(g).get(i) ? up : down).add(ray);
                    }
                }
                risingRays[g][sq] = up.stream().mapToLong(Long::longValue).toArray();
                fallingRays[g][sq] = down.stream().mapToLong(Long::longValue).toArray();
            }
        }
        reach = new long[n];
        slideReach = new long[n];
        for (int sq = 0; sq < n; sq++) {
            reach[sq] = captureTargets(sq, 0, true);
            slideReach[sq] = slides(2, sq, 0) | slides(3, sq, 0);
        }
        sameMovesAndCaptures = rayLists.get(0).equals(rayLists.get(2)) && rayLists.get(1).equals(rayLists.get(3))
                && java.util.Arrays.deepEquals(leapMoves, leapCaptures);
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
     * The squares of group {@code g}'s rays from {@code square}, each up to and including the first
     * occupied one.
     */
    private long slides(int g, int square, long occupied) {
        long targets = 0;
        for (long ray : risingRays[g][square]) {
            long first = ray & occupied;
            first &= -first;
            targets |= ray & (first - 1 | first); // no blocker: first - 1 is all ones
        }
        for (long ray : fallingRays[g][square]) {
            // the nearest blocker, or with none the ray's far end (its lowest square)
            long first = Long.highestOneBit(ray & occupied | ray & -ray);
            targets |= ray & -first;
        }
        return targets;
    }

    /** Every square the piece on {@code square} could capture on, whatever stands in the way. */
    public long reach(int square) {
        return reach[square];
    }

    /** The squares the slides of the piece on {@code square} pass over or end on, on an empty board. */
    public long slideReach(int square) {
        return slideReach[square];
    }

    /**
     * Where the piece on {@code square} can go: empty squares it moves to and squares of
     * {@code enemy} it captures on. Same as {@code quietTargets | captureTargets & enemy}, one pass
     * over the rays when the piece moves as it captures.
     */
    public long targets(int square, long occupied, long enemy, boolean firstMove) {
        if (!sameMovesAndCaptures) {
            return quietTargets(square, occupied, firstMove) | captureTargets(square, occupied, firstMove) & enemy;
        }
        long targets = leapMoves[0][square] | slides(0, square, occupied);
        if (firstMove) {
            targets |= leapMoves[1][square] | slides(1, square, occupied);
        }
        return targets & (~occupied | enemy);
    }

    /**
     * The empty squares the piece on {@code square} can move to without capturing.
     *
     * @param occupied  every occupied square, both sides
     * @param firstMove the piece has not moved yet, so first-move atoms count
     */
    public long quietTargets(int square, long occupied, boolean firstMove) {
        long targets = leapMoves[0][square] | slides(0, square, occupied);
        if (firstMove) {
            targets |= leapMoves[1][square] | slides(1, square, occupied);
        }
        return targets & ~occupied;
    }

    /**
     * The squares the piece on {@code square} attacks: where it would capture an enemy piece. Includes
     * squares its own side stands on and empty squares (it is an attack set: what a king may not walk
     * into).
     */
    public long captureTargets(int square, long occupied, boolean firstMove) {
        long targets = leapCaptures[0][square] | slides(2, square, occupied);
        if (firstMove) {
            targets |= leapCaptures[1][square] | slides(3, square, occupied);
        }
        return targets;
    }
}
