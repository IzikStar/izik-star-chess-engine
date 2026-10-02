package ai.BitBoard;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2 bridge between the headless {@code rules} package and the bitboard engine.
 *
 * <p>Lives in {@code ai.BitBoard} so it can read {@link BitBoard}'s package-visible state
 * directly — the {@code rules} package stays free of any {@code ai.*} internals beyond this
 * one class, and free of {@code main.*} / Swing / AWT entirely.
 *
 * <p>Square indices are the engine's bit order (a8 == 0, h1 == 63):
 * {@code index = rank8Row * 8 + file}.
 *
 * <p>A child that does not read as one move is skipped. Phase 2 added that guard for an
 * en-passant bug that dropped the capturing pawn (one square vacated, none filled), and fixed the
 * bug at the source. Since the Phase 4b rules fixes, perft counts match the published values
 * and Stockfish, and no legal position is known to produce such a child.
 */
public final class BitBoardRules {

    private BitBoardRules() {}

    // ---- FEN -> BitBoard (no BoardState, no AWT) ---------------------------------

    public static BitBoard fromFen(String fen) {
        String[] p = fen.trim().split("\\s+");
        long wK = 0, wQ = 0, wR = 0, wB = 0, wN = 0, wP = 0;
        long bK = 0, bQ = 0, bR = 0, bB = 0, bN = 0, bP = 0;
        int r = 0;
        int c = 0;
        for (int i = 0; i < p[0].length(); i++) {
            char ch = p[0].charAt(i);
            if (ch == '/') {
                r++;
                c = 0;
                continue;
            }
            if (Character.isDigit(ch)) {
                c += ch - '0';
                continue;
            }
            long bit = 1L << (r * 8 + c);
            switch (ch) {
                case 'K' -> wK |= bit;
                case 'Q' -> wQ |= bit;
                case 'R' -> wR |= bit;
                case 'B' -> wB |= bit;
                case 'N' -> wN |= bit;
                case 'P' -> wP |= bit;
                case 'k' -> bK |= bit;
                case 'q' -> bQ |= bit;
                case 'r' -> bR |= bit;
                case 'b' -> bB |= bit;
                case 'n' -> bN |= bit;
                case 'p' -> bP |= bit;
                default -> { /* ignore */ }
            }
            c++;
        }
        boolean whiteToMove = p[1].equals("w");
        String cr = p.length > 2 ? p[2] : "-";
        int ep = -1;
        if (p.length > 3 && !p[3].equals("-")) {
            int file = p[3].charAt(0) - 'a';
            int rank = p[3].charAt(1) - '1' + 1;
            ep = (8 - rank) * 8 + file;
        }
        int half = p.length > 4 ? Integer.parseInt(p[4]) : 0;
        int full = p.length > 5 ? Integer.parseInt(p[5]) : 1;
        return new BitBoard(
                wK, wQ, wR, wB, wN, wP,
                bK, bQ, bR, bB, bN, bP,
                whiteToMove,
                cr.contains("K"), cr.contains("Q"), cr.contains("k"), cr.contains("q"),
                false, false,
                ep,
                full, half,
                null);
    }

    // ---- legality / status ----------------------------------------------------

    public static boolean sideToMoveInCheck(BitBoard b) {
        return b.isSideToMoveInCheck();
    }

    /** Each entry: {@code {fromSquare, toSquare, promotionCharCode-or-0}}. */
    public static List<int[]> legalMoves(BitBoard parent) {
        List<int[]> out = new ArrayList<>();
        boolean whiteMoved = parent.getIsWhiteToMove();
        long ownBefore = own(parent, whiteMoved);
        long kingsBefore = whiteMoved ? parent.whiteKings : parent.blackKings;
        long pawnsBefore = whiteMoved ? parent.whitePawns : parent.blackPawns;
        int promoRow = whiteMoved ? 0 : 7;
        for (BitBoard child : parent.getNextStates()) {
            int[] m = classify(child, whiteMoved, ownBefore, kingsBefore, pawnsBefore, promoRow);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Apply a legal move by matching it against a generated child, returning the resulting FEN,
     * or {@code null} if no legal child matches. A promotion move with no explicit piece
     * ({@code promo == 0}) matches the queen promotion.
     */
    public static String applyMove(BitBoard parent, int from, int to, int promo) {
        boolean whiteMoved = parent.getIsWhiteToMove();
        long ownBefore = own(parent, whiteMoved);
        long kingsBefore = whiteMoved ? parent.whiteKings : parent.blackKings;
        long pawnsBefore = whiteMoved ? parent.whitePawns : parent.blackPawns;
        int promoRow = whiteMoved ? 0 : 7;
        for (BitBoard child : parent.getNextStates()) {
            int[] m = classify(child, whiteMoved, ownBefore, kingsBefore, pawnsBefore, promoRow);
            if (m == null || m[0] != from || m[1] != to) {
                continue;
            }
            boolean promoMatch = promo == 0
                    ? (m[2] == 0 || m[2] == 'q')
                    : m[2] == promo;
            if (promoMatch) {
                return toFen(parent, child, from, to);
            }
        }
        return null;
    }

    /**
     * Recover {from, to, promo} for the side that just moved by diffing its piece bitboards
     * between {@code parent} and {@code child}. Returns {@code null} for a child that does not
     * read as one move (see the class note).
     */
    private static int[] classify(BitBoard child, boolean whiteMoved,
                                  long ownBefore, long kingsBefore, long pawnsBefore, int promoRow) {
        long ownAfter = own(child, whiteMoved);
        long left = ownBefore & ~ownAfter;
        long arrived = ownAfter & ~ownBefore;
        int lc = Long.bitCount(left);
        int ac = Long.bitCount(arrived);

        if (lc == 2 && ac == 2) {
            // castling: emit the king's two-file hop
            int kingFrom = Long.numberOfTrailingZeros(left & kingsBefore);
            int kingFromFile = kingFrom & 7;
            int kingTo = -1;
            long a = arrived;
            while (a != 0) {
                int sq = Long.numberOfTrailingZeros(a);
                a &= a - 1;
                if (Math.abs((sq & 7) - kingFromFile) == 2) {
                    kingTo = sq;
                }
            }
            return kingTo < 0 ? null : new int[]{kingFrom, kingTo, 0};
        }
        if (lc != 1 || ac != 1) {
            return null; // not one move (see the class note): skip
        }

        int from = Long.numberOfTrailingZeros(left);
        int to = Long.numberOfTrailingZeros(arrived);
        int promo = 0;
        if ((pawnsBefore & (1L << from)) != 0 && (to >>> 3) == promoRow) {
            long toBit = 1L << to;
            if (whiteMoved) {
                if ((child.whiteQueens & toBit) != 0) {
                    promo = 'q';
                } else if ((child.whiteRooks & toBit) != 0) {
                    promo = 'r';
                } else if ((child.whiteBishops & toBit) != 0) {
                    promo = 'b';
                } else if ((child.whiteKnights & toBit) != 0) {
                    promo = 'n';
                }
            } else {
                if ((child.blackQueens & toBit) != 0) {
                    promo = 'q';
                } else if ((child.blackRooks & toBit) != 0) {
                    promo = 'r';
                } else if ((child.blackBishops & toBit) != 0) {
                    promo = 'b';
                } else if ((child.blackKnights & toBit) != 0) {
                    promo = 'n';
                }
            }
        }
        return new int[]{from, to, promo};
    }

    // ---- BitBoard -> FEN ----------------------------------------------------

    private static String toFen(BitBoard parent, BitBoard child, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                char ch = BitBoardOperations.bitToChar(child, r * 8 + c);
                if (ch == '-') {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(ch);
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (r < 7) {
                sb.append('/');
            }
        }
        sb.append(child.getIsWhiteToMove() ? " w " : " b ");
        String cr = "" + (child.canWhiteCastleKingSide ? "K" : "")
                + (child.canWhiteCastleQueenSide ? "Q" : "")
                + (child.canBlackCastleKingSide ? "k" : "")
                + (child.canBlackCastleQueenSide ? "q" : "");
        sb.append(cr.isEmpty() ? "-" : cr);

        // En-passant target: only when a pawn just advanced two squares.
        int ep = -1;
        long movedPawnsBefore = parent.getIsWhiteToMove() ? parent.whitePawns : parent.blackPawns;
        if ((movedPawnsBefore & (1L << from)) != 0 && Math.abs((from >>> 3) - (to >>> 3)) == 2) {
            ep = (from + to) / 2;
        }
        sb.append(' ').append(ep == -1 ? "-" : squareName(ep));
        sb.append(' ').append(child.numOfTurnsWithoutCaptureOrPawnMove);
        sb.append(' ').append(child.numOfTurns);
        return sb.toString();
    }

    private static String squareName(int sq) {
        return "" + (char) ('a' + (sq & 7)) + (char) ('0' + (8 - (sq >>> 3)));
    }

    private static long own(BitBoard b, boolean white) {
        return white
                ? b.whiteKings | b.whiteQueens | b.whiteRooks | b.whiteBishops | b.whiteKnights | b.whitePawns
                : b.blackKings | b.blackQueens | b.blackRooks | b.blackBishops | b.blackKnights | b.blackPawns;
    }
}
