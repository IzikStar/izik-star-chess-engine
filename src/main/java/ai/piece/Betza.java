package ai.piece;

import ai.piece.Atom.Kind;
import ai.piece.Atom.Mode;
import ai.piece.Atom.Symmetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Betza notation for {@link Atom}s (Phase 6 R5a), as Fairy-Stockfish reads it, so a piece the
 * owner draws can be written as text, typed as text, and checked by Fairy-Stockfish.
 *
 * <p>The subset: the leapers {@code W F D N A H C Z G} (offsets up to 3), riders as a doubled
 * letter ({@code WW}, {@code NN}) or {@code R B Q}, a range as a number after the letter
 * ({@code W2}: up to two steps), {@code K} for {@code WF}; the modifiers {@code m} (moves only),
 * {@code c} (captures only), {@code i} (first move only) and the directions {@code f b l r v s}
 * and, for the oblique leapers, Fairy-Stockfish's pairs ({@code rf} is the knight's move two
 * forward and one right, {@code fr} one forward and two right, {@code ff} both "two forward"
 * moves, {@code fs} both "one forward" ones). Directions are seen from the piece's owner.
 */
public final class Betza {

    private Betza() {}

    /** Leaper letters by their offset {long, short}. */
    private static final Map<String, Character> LETTERS = new LinkedHashMap<>();
    private static final Map<Character, int[]> OFFSETS = new LinkedHashMap<>();

    static {
        letter('W', 1, 0);
        letter('F', 1, 1);
        letter('D', 2, 0);
        letter('N', 2, 1);
        letter('A', 2, 2);
        letter('H', 3, 0);
        letter('C', 3, 1);
        letter('Z', 3, 2);
        letter('G', 3, 3);
    }

    private static void letter(char c, int a, int b) {
        LETTERS.put(a + "," + b, c);
        OFFSETS.put(c, new int[]{a, b});
    }

    // ---- writing ------------------------------------------------------------------------------

    /** The atoms as Betza text, e.g. {@code mfWimfW2cfF} for the pawn. */
    public static String write(List<Atom> atoms) {
        StringBuilder out = new StringBuilder();
        for (Atom a : atoms) {
            out.append(write(a));
        }
        return out.toString();
    }

    /** One atom; an atom whose directions need several Betza groups becomes several of them. */
    public static String write(Atom atom) {
        int[] o = atom.offsets().get(0);
        int big = Math.max(Math.abs(o[0]), Math.abs(o[1]));
        int small = Math.min(Math.abs(o[0]), Math.abs(o[1]));
        Character letter = LETTERS.get(big + "," + small);
        if (letter == null) {
            throw new IllegalArgumentException("no Betza letter for a move of " + big + " and " + small + " squares");
        }
        Set<List<Integer>> wanted = new LinkedHashSet<>();
        for (int[] off : atom.offsets()) {
            wanted.add(List.of(off[0], off[1]));
        }
        String prefix = (atom.firstMoveOnly() ? "i" : "") + switch (atom.mode()) {
            case MOVE -> "m";
            case CAPTURE -> "c";
            case BOTH -> "";
        };
        boolean slide = atom.kind() == Kind.SLIDE && atom.range() != 1;
        String suffix = !slide ? "" : atom.range() == 0 ? String.valueOf(letter) : String.valueOf(atom.range());
        StringBuilder out = new StringBuilder();
        for (String directions : cover(letter, wanted)) {
            String base = prefix + directions + letter + suffix;
            out.append(shorthand(base));
        }
        return out.toString();
    }

    private static String shorthand(String token) {
        return switch (token) {
            case "WW" -> "R";
            case "FF" -> "B";
            default -> token;
        };
    }

    /** Direction groups whose offsets exactly make up {@code wanted}, largest groups first. */
    private static List<String> cover(char letter, Set<List<Integer>> wanted) {
        List<String> out = new ArrayList<>();
        Set<List<Integer>> left = new LinkedHashSet<>(wanted);
        for (String group : groups(letter)) {
            Set<List<Integer>> covers = directions(letter, group);
            if (!covers.isEmpty() && left.containsAll(covers)) {
                out.add(group);
                left.removeAll(covers);
            }
            if (left.isEmpty()) {
                return out;
            }
        }
        throw new IllegalStateException("cannot write " + wanted + " for " + letter);
    }

    /** The direction groups a letter knows, from all directions ("") down to single moves. */
    private static List<String> groups(char letter) {
        int[] ab = OFFSETS.get(letter);
        if (ab[1] == 0) {
            return List.of("", "v", "s", "f", "b", "l", "r");
        }
        if (ab[0] == ab[1]) {
            return List.of("", "f", "b", "fr", "fl", "br", "bl");
        }
        return List.of("", "ff", "fs", "bb", "bs", "rf", "lf", "rb", "lb", "fr", "fl", "br", "bl");
    }

    /** The offsets {forward, right} a direction group of a letter covers. */
    static Set<List<Integer>> directions(char letter, String group) {
        int a = OFFSETS.get(letter)[0];
        int b = OFFSETS.get(letter)[1];
        Set<List<Integer>> all = new LinkedHashSet<>();
        for (int[] p : new int[][]{{a, b}, {b, a}}) {
            for (int sf : new int[]{1, -1}) {
                for (int sr : new int[]{1, -1}) {
                    all.add(List.of(p[0] * sf, p[1] * sr));
                }
            }
        }
        if (group.isEmpty()) {
            return all;
        }
        Set<List<Integer>> out = new LinkedHashSet<>();
        for (List<Integer> o : all) {
            if (matches(o.get(0), o.get(1), group, b == 0, a == b)) {
                out.add(o);
            }
        }
        return out;
    }

    private static boolean matches(int f, int r, String group, boolean orthogonal, boolean diagonal) {
        if (orthogonal || diagonal) {
            return switch (group) {
                case "f" -> f > 0;
                case "b" -> f < 0;
                case "r" -> r > 0;
                case "l" -> r < 0;
                case "v" -> r == 0;
                case "s" -> f == 0;
                case "fr" -> f > 0 && r > 0;
                case "fl" -> f > 0 && r < 0;
                case "br" -> f < 0 && r > 0;
                case "bl" -> f < 0 && r < 0;
                default -> false;
            };
        }
        boolean upright = Math.abs(f) > Math.abs(r); // the long leg forward or backward
        return switch (group) {
            case "f" -> f > 0;
            case "b" -> f < 0;
            case "r" -> r > 0 && !upright;
            case "l" -> r < 0 && !upright;
            case "v" -> upright;
            case "s" -> !upright;
            case "ff" -> f > 0 && upright;
            case "bb" -> f < 0 && upright;
            case "fs" -> f > 0 && !upright;
            case "bs" -> f < 0 && !upright;
            case "rf" -> f > 0 && r > 0 && upright;
            case "lf" -> f > 0 && r < 0 && upright;
            case "rb" -> f < 0 && r > 0 && upright;
            case "lb" -> f < 0 && r < 0 && upright;
            case "fr" -> f > 0 && r > 0 && !upright;
            case "fl" -> f > 0 && r < 0 && !upright;
            case "br" -> f < 0 && r > 0 && !upright;
            case "bl" -> f < 0 && r < 0 && !upright;
            default -> false;
        };
    }

    // ---- reading ------------------------------------------------------------------------------

    /**
     * The atoms Betza text describes.
     *
     * @throws IllegalArgumentException naming what could not be read
     */
    public static List<Atom> parse(String text) {
        String s = text.replaceAll("\\s+", "");
        if (s.isEmpty()) {
            throw new IllegalArgumentException("empty Betza text");
        }
        List<Atom> atoms = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && Character.isLowerCase(s.charAt(i))) {
                i++;
            }
            String modifiers = s.substring(start, i);
            if (i >= s.length()) {
                throw new IllegalArgumentException("\"" + modifiers + "\" is not followed by a piece letter");
            }
            char c = s.charAt(i++);
            List<Character> letters;
            boolean rider = false;
            switch (c) {
                case 'K' -> letters = List.of('W', 'F');
                case 'R' -> {
                    letters = List.of('W');
                    rider = true;
                }
                case 'B' -> {
                    letters = List.of('F');
                    rider = true;
                }
                case 'Q' -> {
                    letters = List.of('W', 'F');
                    rider = true;
                }
                default -> {
                    if (!OFFSETS.containsKey(c)) {
                        throw new IllegalArgumentException("unknown Betza letter " + c + " in " + text);
                    }
                    letters = List.of(c);
                    if (i < s.length() && s.charAt(i) == c) {
                        rider = true;
                        i++;
                    }
                }
            }
            int range = 0;
            int digits = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (digits < i) {
                range = Integer.parseInt(s.substring(digits, i));
                if (range < 1) {
                    throw new IllegalArgumentException("a range is at least 1: " + text);
                }
                rider = true;
            }
            for (char letter : letters) {
                atoms.addAll(atoms(letter, modifiers, rider, range, text));
            }
        }
        return atoms;
    }

    private static List<Atom> atoms(char letter, String modifiers, boolean rider, int range, String text) {
        boolean first = false;
        Mode mode = Mode.BOTH;
        StringBuilder dirs = new StringBuilder();
        for (char m : modifiers.toCharArray()) {
            switch (m) {
                case 'i' -> first = true;
                case 'm' -> mode = mode == Mode.CAPTURE ? Mode.BOTH : Mode.MOVE;
                case 'c' -> mode = mode == Mode.MOVE ? Mode.BOTH : Mode.CAPTURE;
                case 'f', 'b', 'l', 'r', 'v', 's' -> dirs.append(m);
                default -> throw new IllegalArgumentException("unknown Betza modifier " + m + " in " + text);
            }
        }
        Set<List<Integer>> offsets = new LinkedHashSet<>();
        if (dirs.isEmpty()) {
            offsets.addAll(directions(letter, ""));
        } else {
            for (String group : split(letter, dirs.toString(), text)) {
                Set<List<Integer>> d = directions(letter, group);
                if (d.isEmpty()) {
                    throw new IllegalArgumentException("\"" + group + "\" is no direction of " + letter + " in " + text);
                }
                offsets.addAll(d);
            }
        }
        Kind kind = rider ? Kind.SLIDE : Kind.LEAP;
        int[] ab = OFFSETS.get(letter);
        List<Atom> out = new ArrayList<>();
        if (offsets.equals(directions(letter, ""))) {
            out.add(atom(kind, ab[0], ab[1], Symmetry.ALL, mode, range, first));
            return out;
        }
        // mirror pairs (f, r) and (f, -r) become one SIDEWAYS atom; the rest one atom each
        Set<List<Integer>> left = new LinkedHashSet<>(offsets);
        for (List<Integer> o : offsets) {
            if (!left.contains(o)) {
                continue;
            }
            List<Integer> mirror = List.of(o.get(0), -o.get(1));
            if (o.get(1) != 0 && left.contains(mirror)) {
                out.add(atom(kind, o.get(0), Math.abs(o.get(1)), Symmetry.SIDEWAYS, mode, range, first));
                left.remove(mirror);
            } else {
                out.add(atom(kind, o.get(0), o.get(1), Symmetry.ONE, mode, range, first));
            }
            left.remove(o);
        }
        return out;
    }

    private static Atom atom(Kind kind, int forward, int right, Symmetry symmetry, Mode mode, int range, boolean first) {
        return new Atom(kind, forward, right, symmetry, mode, kind == Kind.SLIDE ? range : 0, first);
    }

    /** Splits direction letters into groups, the oblique pairs first ({@code ffr}: {@code ff} and {@code r}). */
    private static List<String> split(char letter, String dirs, String text) {
        int[] ab = OFFSETS.get(letter);
        boolean oblique = ab[1] != 0 && ab[0] != ab[1];
        boolean diagonal = ab[0] == ab[1];
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < dirs.length()) {
            if (i + 1 < dirs.length()) {
                String pair = dirs.substring(i, i + 2);
                boolean known = oblique ? List.of("ff", "fs", "bb", "bs", "rf", "lf", "rb", "lb", "fr", "fl", "br", "bl").contains(pair)
                        : diagonal && List.of("fr", "fl", "br", "bl").contains(pair);
                if (known) {
                    out.add(pair);
                    i += 2;
                    continue;
                }
            }
            out.add(dirs.substring(i, i + 1));
            i++;
        }
        return out;
    }
}
