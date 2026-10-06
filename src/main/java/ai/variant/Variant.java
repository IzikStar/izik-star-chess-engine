package ai.variant;

import ai.piece.Grid;
import ai.piece.PieceType;

import java.util.List;

/**
 * A game the engine can play (Phase 6 R4): its pieces, the board, the start position and the
 * rules. Standard chess is one variant ({@link Variants#CHESS}); the owner's inventions are others.
 * Everything here is data, so a variant can be saved, sent and edited ({@link VariantJson}).
 *
 * <p>The rules are building blocks (docs/variant-rules.md): a list of win conditions, the first met
 * deciding the game, and settings for the royal pieces, stalemate, repetition, the move limit,
 * forced capture and castling. The built-in variants are presets of them.
 *
 * @param id            short stable name, used in saved games and the protocol ("antichess")
 * @param name          shown to people ("Antichess")
 * @param pieces        the piece types; letters must differ
 * @param grid          the board
 * @param startFen      the start position as FEN (castling rights, en passant, clocks included)
 * @param goals         the ways to win, checked in this order after every move
 * @param royalMode     what the royal pieces mean when a side has several
 * @param stalemate     what having no legal move (and not being checkmated) means
 * @param repetition    the same position three times is a draw
 * @param moveLimit     a draw after this many moves by each side with no capture and no move of a piece
 *                      that promotes (the 50-move rule); 0 for none
 * @param forcedCapture a player who can capture must capture
 * @param castling      how castling works
 */
public record Variant(String id, String name, List<PieceType> pieces, Grid grid, String startFen,
                      List<WinCondition> goals, RoyalMode royalMode, Stalemate stalemate, boolean repetition,
                      int moveLimit, boolean forcedCapture, CastlingRule castling) {

    /** What the royal pieces mean. */
    public enum RoyalMode {
        /** Every royal piece must stay safe: a move may not leave any of them attacked, and an attack on any is check. */
        ALL_SAFE,
        /**
         * A side with two or more royal pieces may leave them attacked, and they can be captured; check,
         * and the rule against leaving a royal piece attacked, apply once a side has one left. A side
         * with none left has lost.
         */
        LAST_STANDING
    }

    /** What having no legal move means for the side to move, when it is not checkmate. */
    public enum Stalemate {
        DRAW, WIN, LOSS
    }

    /** The most moves the move limit may be. */
    public static final int MAX_MOVE_LIMIT = 1000;

    /**
     * The single goal of the first variant files ({@code "goal"} with {@code "checksToWin"}): each is a
     * preset of the building blocks now ({@link #legacy}).
     */
    public enum Goal {
        /** Checkmate the opponent's royal piece; no legal move and not in check is a draw. */
        CHECKMATE,
        /** Lose all your pieces, or have no legal move (antichess): then you win. */
        LOSE_EVERYTHING,
        /** Checkmate, or bring a royal piece to the centre squares. */
        KING_OF_THE_HILL,
        /** Checkmate, or give check {@code checksToWin} times. */
        CHECKS
    }

    public Variant {
        if (id == null || !id.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("a variant id is lower-case letters, digits and '-': " + id);
        }
        pieces = List.copyOf(pieces);
        if (pieces.isEmpty() || pieces.size() > 16) {
            throw new IllegalArgumentException("a variant has 1 to 16 piece types: " + pieces.size());
        }
        if (pieces.stream().map(PieceType::letter).distinct().count() != pieces.size()) {
            throw new IllegalArgumentException("two piece types share a letter in " + id);
        }
        goals = List.copyOf(goals);
        if (goals.isEmpty()) {
            throw new IllegalArgumentException("a variant needs at least one way to win: " + id);
        }
        for (WinCondition.Kind kind : WinCondition.Kind.values()) {
            boolean repeatable = kind == WinCondition.Kind.REACH_SQUARES || kind == WinCondition.Kind.CAPTURE_ALL_OF;
            if (!repeatable && goals.stream().filter(g -> g.kind() == kind).count() > 1) {
                throw new IllegalArgumentException(kind + " is listed twice in " + id);
            }
        }
        if (royalMode == null || stalemate == null || castling == null) {
            throw new IllegalArgumentException("the royal mode, stalemate and castling must be set in " + id);
        }
        if (moveLimit < 0 || moveLimit > MAX_MOVE_LIMIT) {
            throw new IllegalArgumentException("the move limit is 0 (none) to " + MAX_MOVE_LIMIT + ": " + moveLimit);
        }
        boolean royal = pieces.stream().anyMatch(PieceType::royal);
        for (WinCondition g : goals) {
            boolean needsRoyal = switch (g.kind()) {
                case CHECKMATE, CHECKS, BARE_ROYAL -> true;
                case REACH_SQUARES -> g.pieces().isEmpty();
                default -> false;
            };
            if (needsRoyal && !royal) {
                throw new IllegalArgumentException(g.kind() + " needs a royal piece in " + id);
            }
            for (char c : g.pieces().toCharArray()) {
                if (pieces.stream().noneMatch(p -> p.letter() == c)) {
                    throw new IllegalArgumentException(g.kind() + " names " + c + ", which is not a piece of " + id);
                }
            }
            for (String s : g.squares()) {
                if (s.charAt(0) - 'a' >= grid.width() || Integer.parseInt(s.substring(1)) > grid.height()) {
                    throw new IllegalArgumentException(s + " is not on the board of " + id);
                }
            }
        }
    }

    /** A variant in the form of the first variant files: one goal, from which the rules follow. */
    public Variant(String id, String name, List<PieceType> pieces, Grid grid, String startFen,
                   Goal goal, int checksToWin, boolean forcedCapture, boolean castling) {
        this(id, name, pieces, grid, startFen, legacyGoals(goal, checksToWin, grid),
                RoyalMode.ALL_SAFE, goal == Goal.LOSE_EVERYTHING ? Stalemate.WIN : Stalemate.DRAW, true, 50,
                forcedCapture, castling ? CastlingRule.CHESS : CastlingRule.NONE);
    }

    /** The win conditions an old single goal stood for. */
    public static List<WinCondition> legacyGoals(Goal goal, int checksToWin, Grid grid) {
        if ((goal == Goal.CHECKS) != (checksToWin > 0)) {
            throw new IllegalArgumentException("checksToWin is set exactly for the CHECKS goal: " + checksToWin);
        }
        return switch (goal) {
            case CHECKMATE -> List.of(WinCondition.checkmate());
            case LOSE_EVERYTHING -> List.of(WinCondition.loseEverything());
            case KING_OF_THE_HILL -> List.of(WinCondition.checkmate(),
                    WinCondition.reach(WinCondition.centre(grid.width(), grid.height()), ""));
            case CHECKS -> List.of(WinCondition.checkmate(), WinCondition.checks(checksToWin));
        };
    }

    /** The placement field of the start position. */
    public String startPlacement() {
        return startFen.trim().split("\\s+")[0];
    }

    /** Whether the goals include {@code kind}. */
    public boolean has(WinCondition.Kind kind) {
        return goals.stream().anyMatch(g -> g.kind() == kind);
    }

    /** The checks that win ({@link WinCondition.Kind#CHECKS}), or 0 when giving check does not win. */
    public int checksToWin() {
        return goals.stream().filter(g -> g.kind() == WinCondition.Kind.CHECKS).mapToInt(WinCondition::count)
                .findFirst().orElse(0);
    }

    /** The same variant with other goals. */
    public Variant withGoals(List<WinCondition> goals) {
        return new Variant(id, name, pieces, grid, startFen, goals, royalMode, stalemate, repetition, moveLimit,
                forcedCapture, castling);
    }

    /** The same variant with other settings. */
    public Variant withRules(RoyalMode royalMode, Stalemate stalemate, boolean repetition, int moveLimit,
                             CastlingRule castling) {
        return new Variant(id, name, pieces, grid, startFen, goals, royalMode, stalemate, repetition, moveLimit,
                forcedCapture, castling);
    }

    /**
     * The old single goal this variant's rules equal, if they are one of the presets (for the
     * {@code goal} field older readers look at); empty for any other mix.
     */
    public java.util.Optional<Goal> legacyGoal() {
        if (royalMode != RoyalMode.ALL_SAFE || !repetition || moveLimit != 50) {
            return java.util.Optional.empty();
        }
        for (Goal g : Goal.values()) {
            int checks = g == Goal.CHECKS ? checksToWin() : 0;
            if (g == Goal.CHECKS && checks == 0) {
                continue;
            }
            Stalemate s = g == Goal.LOSE_EVERYTHING ? Stalemate.WIN : Stalemate.DRAW;
            if (stalemate == s && goals.equals(legacyGoals(g, checks, grid))) {
                return java.util.Optional.of(g);
            }
        }
        return java.util.Optional.empty();
    }
}
