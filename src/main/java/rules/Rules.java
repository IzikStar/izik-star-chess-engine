package rules;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The single rules authority for IzikStar Chess (Phase 2).
 *
 * <p>Stateless and headless: FEN in, (legal moves + status) out, moves as {@link ChessMove}
 * (UCI). Move generation, check, checkmate and stalemate are delegated to the bitboard engine
 * ({@code ai.BitBoard}, the more-correct of the two legacy paths); the draw rules — 50-move,
 * insufficient material, and (via {@link Game}, which carries history) threefold repetition —
 * are implemented here so exactly one place answers each question.
 *
 * <p>No imports from {@code main.*}, {@code GUI}, {@code javax.swing} or {@code java.awt}: this
 * module is meant to run unchanged inside the Swing app today and inside a headless service later.
 */
public final class Rules {

    private Rules() {}

    public static List<ChessMove> legalMoves(String fen) {
        List<int[]> raw = BitBoardRules.legalMoves(BitBoardRules.fromFen(fen));
        List<ChessMove> out = new ArrayList<>(raw.size());
        for (int[] m : raw) {
            out.add(new ChessMove(m[0], m[1], (char) m[2]));
        }
        return out;
    }

    public static boolean isLegal(String fen, ChessMove move) {
        for (ChessMove m : legalMoves(fen)) {
            if (m.from() == move.from() && m.to() == move.to()
                    && (move.promotion() == 0 || m.promotion() == move.promotion())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCheck(String fen) {
        return BitBoardRules.sideToMoveInCheck(BitBoardRules.fromFen(fen));
    }

    public static boolean hasLegalMove(String fen) {
        return !BitBoardRules.legalMoves(BitBoardRules.fromFen(fen)).isEmpty();
    }

    public static boolean isCheckmate(String fen) {
        BitBoard b = BitBoardRules.fromFen(fen);
        return BitBoardRules.legalMoves(b).isEmpty() && BitBoardRules.sideToMoveInCheck(b);
    }

    public static boolean isStalemate(String fen) {
        BitBoard b = BitBoardRules.fromFen(fen);
        return BitBoardRules.legalMoves(b).isEmpty() && !BitBoardRules.sideToMoveInCheck(b);
    }

    /**
     * Status for a bare FEN — everything except threefold repetition, which needs move history
     * (use {@link Game#status()} for that).
     */
    public static GameStatus status(String fen) {
        Position pos = Position.fromFen(fen);
        BitBoard b = BitBoardRules.fromFen(fen);
        boolean anyMove = !BitBoardRules.legalMoves(b).isEmpty();
        boolean check = BitBoardRules.sideToMoveInCheck(b);
        if (!anyMove) {
            return check ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (isInsufficientMaterial(pos)) {
            return GameStatus.DRAW_INSUFFICIENT_MATERIAL;
        }
        if (pos.halfmoveClock() >= 100) {
            return GameStatus.DRAW_FIFTY_MOVE;
        }
        return check ? GameStatus.CHECK : GameStatus.IN_PROGRESS;
    }

    /** Apply a legal move, returning the resulting FEN. Throws if the move is not legal. */
    public static String applyMove(String fen, ChessMove move) {
        String next = BitBoardRules.applyMove(BitBoardRules.fromFen(fen),
                move.from(), move.to(), move.promotion());
        if (next == null) {
            throw new IllegalArgumentException("illegal move " + move.toUci() + " in " + fen);
        }
        return next;
    }

    public static boolean isInsufficientMaterial(String fen) {
        return isInsufficientMaterial(Position.fromFen(fen));
    }

    /**
     * Dead-position material check: K vs K, K + single minor vs K, and K+B vs K+B with both
     * bishops on the same colour. K+N+N vs K is <em>not</em> treated as insufficient (it cannot
     * be forced but is not dead), matching common engine / FIDE auto-draw behaviour.
     */
    static boolean isInsufficientMaterial(Position pos) {
        int wn = 0;
        int wb = 0;
        int bn = 0;
        int bb = 0;
        int wbColor = -1;
        int bbColor = -1;
        boolean otherMaterial = false;
        for (int sq = 0; sq < 64; sq++) {
            char ch = pos.pieceAt(sq);
            if (ch == 0) {
                continue;
            }
            int sqColor = (Square.file(sq) + Square.rank8Row(sq)) & 1;
            switch (ch) {
                case 'K', 'k' -> { /* kings don't count */ }
                case 'N' -> wn++;
                case 'n' -> bn++;
                case 'B' -> {
                    wb++;
                    wbColor = sqColor;
                }
                case 'b' -> {
                    bb++;
                    bbColor = sqColor;
                }
                default -> otherMaterial = true;
            }
        }
        if (otherMaterial) {
            return false;
        }
        int wMinor = wn + wb;
        int bMinor = bn + bb;
        if (wMinor == 0 && bMinor == 0) {
            return true;
        }
        if (wMinor == 1 && bMinor == 0) {
            return true;
        }
        if (bMinor == 1 && wMinor == 0) {
            return true;
        }
        return wn == 0 && bn == 0 && wb == 1 && bb == 1 && wbColor == bbColor;
    }
}
