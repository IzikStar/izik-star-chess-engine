package rules;

/**
 * Everything a front end needs to know about a move that was just played: what moved, what was
 * captured, the special-move flags, its SAN, and the game status afterwards (threefold included).
 * This is the payload of the session's "move made" event — the browser UI animates and plays
 * sounds from its JSON form.
 */
public record MoveResult(
        ChessMove move,
        String san,
        /** FEN letter of the moving piece (upper case = White). */
        char piece,
        /** FEN letter of the captured piece, or {@code 0}. */
        char captured,
        boolean castling,
        boolean enPassant,
        /** Full-move number of this move (1 for White's and Black's first moves). */
        int moveNumber,
        String fenBefore,
        String fenAfter,
        GameStatus status,
        /** The squares of the royal pieces left in check by the move (chess: the king's), for the board to mark. */
        java.util.List<String> checked) {

    public boolean whiteMoved() {
        return Character.isUpperCase(piece);
    }

    public boolean isCapture() {
        return captured != 0;
    }

    public boolean isPromotion() {
        return move.isPromotion();
    }
}
