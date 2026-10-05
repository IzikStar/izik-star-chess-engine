package ai.variant;

import ai.piece.Grid;
import ai.piece.PieceType;

import java.util.List;

/**
 * A game the engine can play (Phase 6 R4): its pieces, the board, the start position and the
 * rule switches. Standard chess is one variant ({@link Variants#CHESS}); the owner's inventions are
 * others. Everything here is data, so a variant can be saved, sent and edited ({@link VariantJson}).
 *
 * @param id            short stable name, used in saved games and the protocol ("antichess")
 * @param name          shown to people ("Antichess")
 * @param pieces        the piece types; letters must differ
 * @param grid          the board
 * @param startFen      the start position as FEN (castling rights, en passant, clocks included)
 * @param goal          how a game is won
 * @param checksToWin   with {@link Goal#CHECKS}: how many checks win; otherwise 0
 * @param forcedCapture a player who can capture must capture
 * @param castling      castling is allowed (read from the start position's king and rooks)
 */
public record Variant(String id, String name, List<PieceType> pieces, Grid grid, String startFen,
                      Goal goal, int checksToWin, boolean forcedCapture, boolean castling) {

    /** How a game is won. Every goal also ends a game drawn by the 50-move rule. */
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
        if ((goal == Goal.CHECKS) != (checksToWin > 0)) {
            throw new IllegalArgumentException("checksToWin is set exactly for the CHECKS goal: " + checksToWin);
        }
        boolean royal = pieces.stream().anyMatch(PieceType::royal);
        if (goal != Goal.LOSE_EVERYTHING && !royal) {
            throw new IllegalArgumentException(goal + " needs a royal piece in " + id);
        }
    }

    /** The placement field of the start position. */
    public String startPlacement() {
        return startFen.trim().split("\\s+")[0];
    }
}
