package ai.variant;

/**
 * How a variant castles (docs/variant-rules.md). Which pieces take part is said by the pieces
 * themselves: a type with the {@code KING} castling role is the main piece, one with the {@code ROOK}
 * role a partner ({@link ai.piece.PieceType.Castling}); there may be several of each.
 *
 * @param enabled      castling is allowed at all
 * @param steps        how many squares the main piece moves toward the partner; 0 for the chess way,
 *                     onto the second file from that edge (g or c on 8 files)
 * @param partner      where the partner lands, next to the main piece's landing square
 * @param sides        which castlings exist
 * @param safePassage  the main piece may not castle out of, through or into an attacked square; when
 *                     off, only the landing square counts (as for any move)
 */
public record CastlingRule(boolean enabled, int steps, Partner partner, Sides sides, boolean safePassage) {

    public enum Partner {
        /** On the inside: the square the main piece crossed last (chess: f1 or d1). */
        INSIDE,
        /** On the outside: the square beyond the main piece, toward the edge. */
        OUTSIDE
    }

    public enum Sides {
        BOTH, KING_SIDE, QUEEN_SIDE;

        /** Whether the castling toward the higher files ({@code kingSide}) or the lower ones exists. */
        public boolean allows(boolean kingSide) {
            return this == BOTH || (this == KING_SIDE) == kingSide;
        }
    }

    public static final int MAX_STEPS = 14;

    /** Castling as in chess. */
    public static final CastlingRule CHESS = new CastlingRule(true, 0, Partner.INSIDE, Sides.BOTH, true);
    /** No castling. */
    public static final CastlingRule NONE = new CastlingRule(false, 0, Partner.INSIDE, Sides.BOTH, true);

    public CastlingRule {
        if (steps < 0 || steps > MAX_STEPS) {
            throw new IllegalArgumentException("castling steps are 0 (the chess way) to " + MAX_STEPS + ": " + steps);
        }
        if (partner == null || sides == null) {
            throw new IllegalArgumentException("castling needs a partner landing and sides");
        }
    }

    /** True when this castles exactly as chess does (or not at all, when {@code enabled} is off). */
    public boolean chessLike() {
        return steps == 0 && partner == Partner.INSIDE && sides == Sides.BOTH && safePassage;
    }
}
