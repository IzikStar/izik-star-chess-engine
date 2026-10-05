package rules;

import ai.board.Board;
import ai.board.Boards;
import ai.board.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * The single rules authority for IzikStar Chess (Phase 2).
 *
 * <p>Stateless and headless: FEN in, (legal moves + status) out, moves as {@link ChessMove}
 * (UCI). Move generation, check, checkmate and stalemate are delegated to the engine's
 * {@link Board} (Phase 6: through that interface only, not a particular board); the draw rules — 50-move,
 * insufficient material, and (via {@link Game}, which carries history) threefold repetition —
 * are implemented here so exactly one place answers each question.
 *
 * <p>No imports from {@code main.*}, {@code GUI}, {@code javax.swing} or {@code java.awt}: this
 * module is meant to run unchanged inside the Swing app today and inside a headless service later.
 */
public final class Rules {

    private Rules() {}

    public static List<ChessMove> legalMoves(String fen) {
        return legalMoves(Boards.fromFen(fen));
    }

    private static List<ChessMove> legalMoves(Board board) {
        int[] raw = board.legalMoves();
        List<ChessMove> out = new ArrayList<>(raw.length);
        for (int m : raw) {
            out.add(new ChessMove(Move.from(m), Move.to(m), Move.promotion(m)));
        }
        return out;
    }

    /** Legal moves + status from a single bitboard pass — for callers that need both. */
    public static Evaluation evaluate(String fen) {
        Position pos = Position.fromFen(fen);
        Board b = Boards.fromFen(fen);
        List<ChessMove> moves = legalMoves(b);
        boolean check = b.inCheck();
        GameStatus status;
        if (moves.isEmpty()) {
            status = check ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        } else if (isInsufficientMaterial(pos)) {
            status = GameStatus.DRAW_INSUFFICIENT_MATERIAL;
        } else if (pos.halfmoveClock() >= 100) {
            status = GameStatus.DRAW_FIFTY_MOVE;
        } else {
            status = check ? GameStatus.CHECK : GameStatus.IN_PROGRESS;
        }
        return new Evaluation(moves, status);
    }

    /** Immutable pair returned by {@link #evaluate(String)}. */
    public static final class Evaluation {
        public final List<ChessMove> legalMoves;
        public final GameStatus status;

        Evaluation(List<ChessMove> legalMoves, GameStatus status) {
            this.legalMoves = legalMoves;
            this.status = status;
        }
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
        return Boards.fromFen(fen).inCheck();
    }

    public static boolean hasLegalMove(String fen) {
        return !Boards.fromFen(fen).children().isEmpty();
    }

    public static boolean isCheckmate(String fen) {
        Board b = Boards.fromFen(fen);
        return b.children().isEmpty() && b.inCheck();
    }

    public static boolean isStalemate(String fen) {
        Board b = Boards.fromFen(fen);
        return b.children().isEmpty() && !b.inCheck();
    }

    /**
     * Status for a bare FEN — everything except threefold repetition, which needs move history
     * (use {@link Game#status()} for that).
     */
    public static GameStatus status(String fen) {
        Position pos = Position.fromFen(fen);
        Board b = Boards.fromFen(fen);
        boolean anyMove = !b.children().isEmpty();
        boolean check = b.inCheck();
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
        Board next = Boards.fromFen(fen).play(Move.of(move.from(), move.to(), move.promotion()));
        if (next == null) {
            throw new IllegalArgumentException("illegal move " + move.toUci() + " in " + fen);
        }
        return next.toFen();
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
