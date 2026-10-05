package rules;

import ai.board.Board;
import ai.piece.StandardPieces;
import ai.variant.Variant;
import ai.variant.Variants;
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
        return legalMoves(Variants.CHESS, fen);
    }

    public static List<ChessMove> legalMoves(Variant variant, String fen) {
        return legalMoves(Boards.fromFen(variant, fen));
    }

    private static List<ChessMove> legalMoves(Board board) {
        int[] raw = board.legalMoves();
        List<ChessMove> out = new ArrayList<>(raw.length);
        for (int m : raw) {
            out.add(new ChessMove(Move.from(m), Move.to(m), Move.promotion(m)));
        }
        return out;
    }

    /** Legal moves + status from a single board — for callers that need both. */
    public static Evaluation evaluate(String fen) {
        return evaluate(Variants.CHESS, fen);
    }

    public static Evaluation evaluate(Variant variant, String fen) {
        Board b = Boards.fromFen(variant, fen);
        return new Evaluation(legalMoves(b), status(variant, b, fen));
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
        return isLegal(Variants.CHESS, fen, move);
    }

    public static boolean isLegal(Variant variant, String fen, ChessMove move) {
        for (ChessMove m : legalMoves(variant, fen)) {
            if (m.from() == move.from() && m.to() == move.to()
                    && (move.promotion() == 0 || m.promotion() == move.promotion())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCheck(String fen) {
        return isCheck(Variants.CHESS, fen);
    }

    public static boolean isCheck(Variant variant, String fen) {
        return Boards.fromFen(variant, fen).inCheck();
    }

    /**
     * The squares of the side to move's royal pieces that are attacked, top-left first (chess: the
     * king's square when in check, else none). A made variant may have several royal pieces.
     */
    public static java.util.List<String> checkedSquares(Variant variant, String fen) {
        long checked = Boards.fromFen(variant, fen).checkedRoyals();
        int width = variant.grid().width();
        int height = variant.grid().height();
        java.util.List<String> squares = new java.util.ArrayList<>();
        for (long b = checked; b != 0; b &= b - 1) {
            int sq = Long.numberOfTrailingZeros(b);
            squares.add("" + (char) ('a' + sq % width) + (height - sq / width));
        }
        return squares;
    }

    public static boolean hasLegalMove(String fen) {
        return !Boards.fromFen(fen).children().isEmpty();
    }

    public static boolean isCheckmate(String fen) {
        return status(fen) == GameStatus.CHECKMATE;
    }

    public static boolean isStalemate(String fen) {
        return status(fen) == GameStatus.STALEMATE;
    }

    /**
     * Status for a bare FEN — everything except threefold repetition, which needs move history
     * (use {@link Game#status()} for that).
     */
    public static GameStatus status(String fen) {
        return status(Variants.CHESS, fen);
    }

    public static GameStatus status(Variant variant, String fen) {
        return status(variant, Boards.fromFen(variant, fen), fen);
    }

    /**
     * The board says whether and how the game is over for the player to move ({@link Outcome});
     * this names why. The variant's goal comes first, then a position without moves (mate or
     * stalemate; in antichess a win), then the draw rules.
     */
    private static GameStatus status(Variant variant, Board b, String fen) {
        boolean check = b.inCheck();
        switch (b.outcome()) {
            case WIN:
                return Position.fromFen(fen).pieces().stream().anyMatch(p -> isOwn(p, b.sideToMove()))
                        ? GameStatus.NO_MOVES_LEFT : GameStatus.NO_PIECES_LEFT;
            case LOSS:
                if (b.goalReached()) {
                    return variant.goal() == Variant.Goal.CHECKS ? GameStatus.CHECKS_GIVEN : GameStatus.HILL_REACHED;
                }
                return GameStatus.CHECKMATE;
            case DRAW:
                if (b.children().isEmpty()) {
                    return GameStatus.STALEMATE;
                }
                return insufficientMaterialCounts(variant) && isInsufficientMaterial(Position.fromFen(fen))
                        ? GameStatus.DRAW_INSUFFICIENT_MATERIAL : GameStatus.DRAW_FIFTY_MOVE;
            default:
                if (insufficientMaterialCounts(variant) && isInsufficientMaterial(Position.fromFen(fen))) {
                    return GameStatus.DRAW_INSUFFICIENT_MATERIAL;
                }
                return check ? GameStatus.CHECK : GameStatus.IN_PROGRESS;
        }
    }

    private static boolean isOwn(char piece, int player) {
        return Character.isUpperCase(piece) == (player == 0);
    }

    /**
     * Dead positions by material are a chess rule: in King of the Hill a lone king can still win,
     * in antichess losing material is the point, and an invented piece set has no known table.
     */
    private static boolean insufficientMaterialCounts(Variant variant) {
        return variant.goal() == Variant.Goal.CHECKMATE && variant.pieces().equals(StandardPieces.ALL);
    }

    /** Apply a legal move, returning the resulting FEN. Throws if the move is not legal. */
    public static String applyMove(String fen, ChessMove move) {
        return applyMove(Variants.CHESS, fen, move);
    }

    public static String applyMove(Variant variant, String fen, ChessMove move) {
        Board next = Boards.fromFen(variant, fen).play(Move.of(move.from(), move.to(), move.promotion()));
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
