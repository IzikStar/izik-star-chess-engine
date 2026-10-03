package ai.BitBoard;


import java.util.Random;

public class ZobristHashing {
    private static final long[][][] zobristTable = new long[2][7][64]; // [צבע][סוג כלי][משבצת]
    private static final long BLACK_TO_MOVE;
    /** K, Q, k, q castling rights; then White has castled, Black has castled. */
    private static final long[] FLAGS = new long[6];
    private static final long[] EN_PASSANT = new long[64];

    static {
        Random random = new Random(0);
        for (int color = 0; color < 2; color++) {
            for (int piece = 1; piece <= 6; piece++) {
                for (int square = 0; square < 64; square++) {
                    zobristTable[color][piece][square] = random.nextLong();
                }
            }
        }
        BLACK_TO_MOVE = random.nextLong();
        for (int i = 0; i < FLAGS.length; i++) {
            FLAGS[i] = random.nextLong();
        }
        for (int square = 0; square < 64; square++) {
            EN_PASSANT[square] = random.nextLong();
        }
    }

    /**
     * Position hash for the search's repetition check: piece placement + side to move. Before
     * Phase 3 pawns hashed as "empty" and the side to move was ignored, so any two pawn moves
     * looked like a threefold repetition and the search scored them as one.
     */
    public static long computeHash(BitBoard board) {
        long hash = board.getIsWhiteToMove() ? 0L : BLACK_TO_MOVE;
        // colors: 1 white, 0 black; pieces: 1 king, 2 queen, 3 rook, 4 bishop, 5 knight, 6 pawn
        hash = hashPieces(hash, 1, 1, board.whiteKings);
        hash = hashPieces(hash, 1, 2, board.whiteQueens);
        hash = hashPieces(hash, 1, 3, board.whiteRooks);
        hash = hashPieces(hash, 1, 4, board.whiteBishops);
        hash = hashPieces(hash, 1, 5, board.whiteKnights);
        hash = hashPieces(hash, 1, 6, board.whitePawns);
        hash = hashPieces(hash, 0, 1, board.blackKings);
        hash = hashPieces(hash, 0, 2, board.blackQueens);
        hash = hashPieces(hash, 0, 3, board.blackRooks);
        hash = hashPieces(hash, 0, 4, board.blackBishops);
        hash = hashPieces(hash, 0, 5, board.blackKnights);
        hash = hashPieces(hash, 0, 6, board.blackPawns);
        return hash;
    }

    /**
     * Key of the transposition table (Phase 5b): {@link #computeHash} plus everything else the search
     * and the evaluation read from a position: castling rights, the en-passant square, whether each
     * side has castled and the move number (the evaluation has opening-only terms). Two positions
     * with the same key are scored the same, short of a hash collision. Left out: the 50-move
     * counter and the repetition history (docs/phase-5b-research.md §3.3).
     */
    public static long searchKey(BitBoard board) {
        long key = computeHash(board);
        if (board.canWhiteCastleKingSide) key ^= FLAGS[0];
        if (board.canWhiteCastleQueenSide) key ^= FLAGS[1];
        if (board.canBlackCastleKingSide) key ^= FLAGS[2];
        if (board.canBlackCastleQueenSide) key ^= FLAGS[3];
        if (board.hasWhiteCastled) key ^= FLAGS[4];
        if (board.hasBlackCastled) key ^= FLAGS[5];
        if (board.enPassantTile >= 0 && board.enPassantTile < 64) key ^= EN_PASSANT[board.enPassantTile];
        return key ^ board.numOfTurns * 0x9E3779B97F4A7C15L;
    }

    private static long hashPieces(long hash, int color, int piece, long squares) {
        for (long rest = squares; rest != 0; rest &= rest - 1) {
            hash ^= zobristTable[color][piece][Long.numberOfTrailingZeros(rest)];
        }
        return hash;
    }
}
