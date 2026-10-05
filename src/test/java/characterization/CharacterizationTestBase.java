package characterization;

import ai.board.Board;
import ai.board.Boards;
import rules.ChessMove;
import rules.Rules;

/**
 * Characterization tests — see docs/phase-0-notes.md, docs/phase-2-research.md and
 * docs/phase-3-research.md.
 *
 * <p>Originally these pinned what two independent rule engines did (bugs included). Phase 2
 * unified them behind {@code rules.Rules}; Phase 3 deleted the object model ({@code BoardState},
 * {@code pieces.*}, {@code main.Move}), so the old "OO path" assertions now run against the game
 * API ({@code rules.Rules} / {@code rules.Game}) — the same questions, the same expected answers.
 * The "board path" ({@link Board}, what the search runs on) is still checked directly.
 */
public abstract class CharacterizationTestBase {

    // ---- Well-known positions -------------------------------------------------

    protected static final String START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** 1.f3 e5 2.g4 Qh4# — White is to move and checkmated. */
    protected static final String FOOLS_MATE =
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";

    /** Rook on a8 mates the castled Black king; Black is to move and checkmated. */
    protected static final String BACK_RANK_MATE =
            "R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1";

    /** Kf6 + Qf7 vs Kh8; Black is to move, not in check, and has no legal move. */
    protected static final String STALEMATE =
            "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1";

    /** Both sides still have both rooks and the king on its home square. */
    protected static final String CASTLING_OPEN =
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

    /** Black has just played ...f7-f5; White can take e5xf6 e.p. */
    protected static final String EN_PASSANT =
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3";

    /** White pawn on a7, both kings far away — a7-a8 promotes. */
    protected static final String PROMOTION =
            "8/P6k/8/8/8/8/8/7K w - - 0 1";

    // ---- Helpers ----------------------------------------------------------

    protected static Board board(String fen) {
        return Boards.fromFen(fen);
    }

    /**
     * The search board's verdict in the old bitboard's numbers: 1 the game goes on, 0 a draw
     * (stalemate or the 50-move rule), {@code Integer.MAX_VALUE} White is mated,
     * {@code Integer.MIN_VALUE} Black is mated.
     */
    protected static int status(Board b) {
        if (b.children().isEmpty()) {
            return !b.inCheck() ? 0 : b.sideToMove() == 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }
        return b.isOver() ? 0 : 1;
    }

    /** Legal moves for the side to move, via the game API ({@code rules.Rules}). */
    protected static int legalMovesApi(String fen) {
        return Rules.legalMoves(fen).size();
    }

    /** Legal moves for the side to move, via the search board. */
    protected static int legalMovesBit(String fen) {
        return board(fen).children().size();
    }

    /** True if {@code uci} is legal in {@code fen}. */
    protected static boolean legal(String fen, String uci) {
        return Rules.isLegal(fen, ChessMove.fromUci(uci));
    }
}
