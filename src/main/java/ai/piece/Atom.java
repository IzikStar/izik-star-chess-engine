package ai.piece;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One building block of how a piece moves (Phase 6 R2, docs/phase-6-research.md §2). Offsets are
 * seen from the piece's owner: {@code forward} toward the opponent, {@code right} to the owner's
 * right. A piece is a list of atoms.
 *
 * @param kind      {@link Kind#LEAP} jumps straight to the offset; {@link Kind#SLIDE} repeats the
 *                  offset until a piece is in the way (a capture may land on that piece)
 * @param forward   rows toward the opponent (negative: backward)
 * @param right     files to the owner's right (negative: left)
 * @param symmetry  which mirror images of the offset also count
 * @param mode      whether the atom moves to empty squares, captures, or both
 * @param range     for a slide, at most this many steps; 0 = no limit. Ignored for a leap
 * @param firstMoveOnly only while the piece has not moved yet (the pawn's double step)
 */
public record Atom(Kind kind, int forward, int right, Symmetry symmetry, Mode mode, int range,
                   boolean firstMoveOnly) {

    public enum Kind { LEAP, SLIDE }

    /** Whether an atom goes to empty squares, captures, or both. */
    public enum Mode {
        MOVE, CAPTURE, BOTH;

        public boolean moves() {
            return this != CAPTURE;
        }

        public boolean captures() {
            return this != MOVE;
        }
    }

    /** Which mirror images of an offset an atom also covers. */
    public enum Symmetry {
        /** Just the offset as given. */
        ONE,
        /** The offset and its left-right mirror: forward stays forward. */
        SIDEWAYS,
        /** All eight rotations and reflections: the same in every direction. */
        ALL
    }

    public Atom {
        if (forward == 0 && right == 0) {
            throw new IllegalArgumentException("an atom must go somewhere");
        }
        if (range < 0) {
            throw new IllegalArgumentException("range is 0 (no limit) or more: " + range);
        }
    }

    public static Atom leap(int forward, int right, Symmetry symmetry, Mode mode) {
        return new Atom(Kind.LEAP, forward, right, symmetry, mode, 0, false);
    }

    public static Atom slide(int forward, int right, Symmetry symmetry, Mode mode) {
        return new Atom(Kind.SLIDE, forward, right, symmetry, mode, 0, false);
    }

    public Atom withRange(int steps) {
        return new Atom(kind, forward, right, symmetry, mode, steps, firstMoveOnly);
    }

    public Atom firstMove() {
        return new Atom(kind, forward, right, symmetry, mode, range, true);
    }

    /** The offsets this atom covers, as {forward, right} pairs, each once. */
    public List<int[]> offsets() {
        Set<List<Integer>> seen = new LinkedHashSet<>();
        int f = forward;
        int r = right;
        switch (symmetry) {
            case ONE -> seen.add(List.of(f, r));
            case SIDEWAYS -> {
                seen.add(List.of(f, r));
                seen.add(List.of(f, -r));
            }
            case ALL -> {
                for (int[] p : new int[][]{{f, r}, {r, f}}) {
                    for (int sf : new int[]{1, -1}) {
                        for (int sr : new int[]{1, -1}) {
                            seen.add(List.of(p[0] * sf, p[1] * sr));
                        }
                    }
                }
            }
        }
        List<int[]> out = new ArrayList<>(seen.size());
        for (List<Integer> o : seen) {
            out.add(new int[]{o.get(0), o.get(1)});
        }
        return out;
    }
}
