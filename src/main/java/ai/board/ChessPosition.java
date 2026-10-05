package ai.board;

/**
 * A {@link Board} holding the six chess pieces, as the chess evaluation reads it (Phase 6 R3): piece
 * sets by player and type, castling, and attacks. Any board that plays standard chess pieces can
 * offer it, so the chess evaluation does not depend on how the board stores them.
 *
 * <p>Squares are bits of a {@code long}, a8 = bit 0 ... h1 = bit 63; players 0 White, 1 Black.
 */
public interface ChessPosition extends Board {

    /** Piece types, in the order of {@code ai.piece.StandardPieces.ALL}. */
    int KING = 0;
    int QUEEN = 1;
    int ROOK = 2;
    int BISHOP = 3;
    int KNIGHT = 4;
    int PAWN = 5;

    /** The squares of {@code player}'s pieces of {@code type}. */
    long pieces(int player, int type);

    /** Every square {@code player}'s pieces stand on. */
    long occupied(int player);

    /** Every square {@code player} attacks, including squares its own pieces stand on. */
    long attackedBy(int player);

    /** Whether {@code player}'s king stands attacked. */
    boolean inCheck(int player);

    /** Whether the side to move has any legal move. */
    boolean hasLegalMove();

    /** Plies since the last capture or pawn move. */
    int halfmoveClock();

    /** The FEN move number: 1 at the start, +1 after each Black move. */
    int fullmoveNumber();

    boolean canCastle(int player, boolean kingSide);

    /** Whether {@code player} has castled in this game (as far as the board knows). */
    boolean hasCastled(int player);
}
