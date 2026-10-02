package ai.BitBoard;

/**
 * Attack lookups for the search (Phase 4b, docs/phase-4b-research.md §3.3): which squares a side
 * attacks, and whether one square is attacked. Knights, kings and pawns use tables built once;
 * rooks, bishops and queens walk their rays up to the first piece in the way.
 *
 * <p>Squares are numbered a8 = 0 … h1 = 63, so row 0 is the eighth rank. Colors: 1 is White,
 * 0 is Black. An attacked set includes squares the side's own pieces stand on.
 */
final class Attacks {

    private Attacks() {}

    static final long[] KNIGHT = new long[64];
    static final long[] KING = new long[64];
    /** {@code PAWN[color][sq]}: the squares a pawn of that color standing on {@code sq} attacks. */
    static final long[][] PAWN = new long[2][64];

    static {
        int[][] jumps = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        for (int sq = 0; sq < 64; sq++) {
            int row = sq >>> 3;
            int col = sq & 7;
            for (int[] jump : jumps) {
                KNIGHT[sq] |= bit(row + jump[0], col + jump[1]);
            }
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr != 0 || dc != 0) {
                        KING[sq] |= bit(row + dr, col + dc);
                    }
                }
            }
            PAWN[1][sq] = bit(row - 1, col - 1) | bit(row - 1, col + 1); // White moves toward row 0
            PAWN[0][sq] = bit(row + 1, col - 1) | bit(row + 1, col + 1);
        }
    }

    private static long bit(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8 ? 1L << (row * 8 + col) : 0L;
    }

    /** The squares from {@code sq} in one direction, up to and including the first occupied one. */
    private static long ray(int sq, long occupied, int dr, int dc) {
        long squares = 0;
        int row = (sq >>> 3) + dr;
        int col = (sq & 7) + dc;
        while (row >= 0 && row < 8 && col >= 0 && col < 8) {
            long b = 1L << (row * 8 + col);
            squares |= b;
            if ((occupied & b) != 0) {
                break;
            }
            row += dr;
            col += dc;
        }
        return squares;
    }

    static long rook(int sq, long occupied) {
        return ray(sq, occupied, -1, 0) | ray(sq, occupied, 1, 0) | ray(sq, occupied, 0, -1) | ray(sq, occupied, 0, 1);
    }

    static long bishop(int sq, long occupied) {
        return ray(sq, occupied, -1, -1) | ray(sq, occupied, -1, 1) | ray(sq, occupied, 1, -1) | ray(sq, occupied, 1, 1);
    }

    /** Is {@code sq} attacked by a piece of color {@code by}? */
    static boolean attacked(BitBoard b, int sq, int by) {
        boolean white = by == 1;
        // a pawn of color `by` attacks sq exactly from the squares a pawn of the other color on sq would attack
        if ((PAWN[1 - by][sq] & (white ? b.whitePawns : b.blackPawns)) != 0) {
            return true;
        }
        if ((KNIGHT[sq] & (white ? b.whiteKnights : b.blackKnights)) != 0) {
            return true;
        }
        if ((KING[sq] & (white ? b.whiteKings : b.blackKings)) != 0) {
            return true;
        }
        long occupied = b.whitePieces | b.blackPieces;
        long straight = white ? b.whiteRooks | b.whiteQueens : b.blackRooks | b.blackQueens;
        if (straight != 0 && (rook(sq, occupied) & straight) != 0) {
            return true;
        }
        long diagonal = white ? b.whiteBishops | b.whiteQueens : b.blackBishops | b.blackQueens;
        return diagonal != 0 && (bishop(sq, occupied) & diagonal) != 0;
    }

    /** Every square attacked by {@code color}. */
    static long all(BitBoard b, int color) {
        boolean white = color == 1;
        long occupied = b.whitePieces | b.blackPieces;
        long squares = 0;
        for (long p = white ? b.whitePawns : b.blackPawns; p != 0; p &= p - 1) {
            squares |= PAWN[color][Long.numberOfTrailingZeros(p)];
        }
        for (long p = white ? b.whiteKnights : b.blackKnights; p != 0; p &= p - 1) {
            squares |= KNIGHT[Long.numberOfTrailingZeros(p)];
        }
        for (long p = white ? b.whiteKings : b.blackKings; p != 0; p &= p - 1) {
            squares |= KING[Long.numberOfTrailingZeros(p)];
        }
        for (long p = white ? b.whiteRooks | b.whiteQueens : b.blackRooks | b.blackQueens; p != 0; p &= p - 1) {
            squares |= rook(Long.numberOfTrailingZeros(p), occupied);
        }
        for (long p = white ? b.whiteBishops | b.whiteQueens : b.blackBishops | b.blackQueens; p != 0; p &= p - 1) {
            squares |= bishop(Long.numberOfTrailingZeros(p), occupied);
        }
        return squares;
    }
}
