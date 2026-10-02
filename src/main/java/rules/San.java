package rules;

import java.util.List;

/**
 * Standard Algebraic Notation for a legal move ({@code "Nbd7"}, {@code "exd6"}, {@code "O-O"},
 * {@code "e8=Q+"}, {@code "Qxf7#"}). Headless replacement for {@code main.Move}'s string builder.
 */
public final class San {

    private San() {}

    public static String of(String fen, ChessMove move) {
        Position pos = Position.fromFen(fen);
        char piece = pos.pieceAt(move.from());
        if (piece == 0) {
            throw new IllegalArgumentException("no piece on " + Square.name(move.from()) + " in " + fen);
        }
        char kind = Character.toUpperCase(piece);
        StringBuilder san = new StringBuilder();

        if (kind == 'K' && Math.abs(Square.file(move.from()) - Square.file(move.to())) == 2) {
            san.append(Square.file(move.to()) > Square.file(move.from()) ? "O-O" : "O-O-O");
        } else {
            boolean capture = isCapture(pos, move);
            if (kind == 'P') {
                if (capture) {
                    san.append((char) ('a' + Square.file(move.from()))).append('x');
                }
            } else {
                san.append(kind).append(disambiguation(fen, pos, move, piece));
                if (capture) {
                    san.append('x');
                }
            }
            san.append(Square.name(move.to()));
            if (move.isPromotion()) {
                san.append('=').append(Character.toUpperCase(move.promotion()));
            }
        }

        GameStatus after = Rules.status(Rules.applyMove(fen, move));
        if (after == GameStatus.CHECKMATE) {
            san.append('#');
        } else if (after == GameStatus.CHECK || (after.isGameOver() && Rules.isCheck(Rules.applyMove(fen, move)))) {
            san.append('+');
        }
        return san.toString();
    }

    /** True for an ordinary capture or an en-passant capture. */
    public static boolean isCapture(Position pos, ChessMove move) {
        if (pos.pieceAt(move.to()) != 0) {
            return true;
        }
        return isEnPassant(pos, move);
    }

    public static boolean isEnPassant(Position pos, ChessMove move) {
        char piece = pos.pieceAt(move.from());
        return Character.toUpperCase(piece) == 'P'
                && move.to() == pos.epSquare()
                && Square.file(move.from()) != Square.file(move.to());
    }

    private static String disambiguation(String fen, Position pos, ChessMove move, char piece) {
        List<ChessMove> legal = Rules.legalMoves(fen);
        boolean ambiguous = false;
        boolean sameFile = false;
        boolean sameRank = false;
        for (ChessMove other : legal) {
            if (other.to() != move.to() || other.from() == move.from() || pos.pieceAt(other.from()) != piece) {
                continue;
            }
            ambiguous = true;
            sameFile |= Square.file(other.from()) == Square.file(move.from());
            sameRank |= Square.rank8Row(other.from()) == Square.rank8Row(move.from());
        }
        if (!ambiguous) {
            return "";
        }
        String square = Square.name(move.from());
        if (!sameFile) {
            return square.substring(0, 1);
        }
        if (!sameRank) {
            return square.substring(1);
        }
        return square;
    }
}
