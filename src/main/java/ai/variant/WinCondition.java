package ai.variant;

import java.util.List;

/**
 * One way to win a game (docs/variant-rules.md). A variant lists several; after every move they are
 * checked in the listed order, and the first one met decides the game.
 *
 * @param kind    which condition
 * @param count   with {@link Kind#CHECKS}: how many checks win; otherwise 0
 * @param squares with {@link Kind#REACH_SQUARES}: the squares ("d4"); otherwise empty
 * @param pieces  with {@link Kind#REACH_SQUARES}: the letters of the piece types that count, "" for
 *                the royal pieces; with {@link Kind#CAPTURE_ALL_OF}: the letters of the types to
 *                capture; otherwise ""
 */
public record WinCondition(Kind kind, int count, List<String> squares, String pieces) {

    public enum Kind {
        /** The opponent is in check and has no legal move. */
        CHECKMATE,
        /** One of your pieces of the given types stands on one of the given squares. */
        REACH_SQUARES,
        /** You have given check {@code count} times. */
        CHECKS,
        /** The opponent has no piece of the given types left. */
        CAPTURE_ALL_OF,
        /** The opponent has nothing left but royal pieces. */
        BARE_ROYAL,
        /** You have no pieces left, or no legal move (antichess). */
        LOSE_EVERYTHING
    }

    public static final int MAX_CHECKS = 99;

    public WinCondition {
        squares = squares == null ? List.of() : List.copyOf(squares);
        pieces = pieces == null ? "" : pieces;
        switch (kind) {
            case CHECKS -> {
                if (count < 1 || count > MAX_CHECKS) {
                    throw new IllegalArgumentException("checks to win are 1 to " + MAX_CHECKS + ": " + count);
                }
            }
            case REACH_SQUARES -> {
                if (squares.isEmpty()) {
                    throw new IllegalArgumentException("a squares goal needs at least one square");
                }
                for (String s : squares) {
                    if (!s.matches("[a-p](1[0-6]|[1-9])")) {
                        throw new IllegalArgumentException("not a square: " + s);
                    }
                }
            }
            case CAPTURE_ALL_OF -> {
                if (pieces.isEmpty()) {
                    throw new IllegalArgumentException("a capture-all goal needs at least one piece type");
                }
            }
            default -> { }
        }
        if (kind != Kind.CHECKS && count != 0) {
            throw new IllegalArgumentException("only the checks goal has a count");
        }
        if (kind != Kind.REACH_SQUARES && !squares.isEmpty()) {
            throw new IllegalArgumentException("only the squares goal has squares");
        }
        if (kind != Kind.REACH_SQUARES && kind != Kind.CAPTURE_ALL_OF && !pieces.isEmpty()) {
            throw new IllegalArgumentException(kind + " takes no piece types");
        }
        if (!pieces.matches("[A-Z]*")) {
            throw new IllegalArgumentException("piece types are upper-case letters: " + pieces);
        }
    }

    public static WinCondition checkmate() {
        return new WinCondition(Kind.CHECKMATE, 0, List.of(), "");
    }

    public static WinCondition loseEverything() {
        return new WinCondition(Kind.LOSE_EVERYTHING, 0, List.of(), "");
    }

    public static WinCondition checks(int count) {
        return new WinCondition(Kind.CHECKS, count, List.of(), "");
    }

    /** Bring a piece of one of {@code pieces} ("" = a royal piece) to one of {@code squares}. */
    public static WinCondition reach(List<String> squares, String pieces) {
        return new WinCondition(Kind.REACH_SQUARES, 0, squares, pieces);
    }

    public static WinCondition captureAllOf(String pieces) {
        return new WinCondition(Kind.CAPTURE_ALL_OF, 0, List.of(), pieces);
    }

    public static WinCondition bareRoyal() {
        return new WinCondition(Kind.BARE_ROYAL, 0, List.of(), "");
    }

    /** The middle squares of a board: one or two middle files by one or two middle ranks (King of the Hill). */
    public static List<String> centre(int width, int height) {
        List<String> out = new java.util.ArrayList<>();
        for (int row = (height - 1) / 2; row <= height / 2; row++) {
            for (int col = (width - 1) / 2; col <= width / 2; col++) {
                out.add("" + (char) ('a' + col) + (height - row));
            }
        }
        return out;
    }
}
