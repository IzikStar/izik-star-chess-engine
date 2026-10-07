package ai.eval;

/** Bitboard masks for the chess evaluation (bit 0 = a8, bit 63 = h1). */
final class BoardParts {

    private BoardParts() {}

    static final long FIRST_RANK = 0xFF00000000000000L;
    static final long SECOND_RANK = 0x00FF000000000000L;
    static final long THIRD_RANK = 0x0000FF0000000000L;
    static final long FOURTH_RANK = 0x000000FF00000000L;
    static final long FIFTH_RANK = 0x00000000FF000000L;
    static final long SIXTH_RANK = 0x0000000000FF0000L;
    static final long SEVENTH_RANK = 0x000000000000FF00L;
    static final long EIGHTH_RANK = 0x00000000000000FFL;

    static final long A_FILE = 0x0101010101010101L;
    static final long CENTER = 0x0000001818000000L;
    static final long FIRST_FIVE_RANKS = 0xFFFFFFFFFF000000L;
    static final long BACK_FIVE_RANKS = 0x000000FFFFFFFFFFL;

    // single squares
    static final long B1 = 0x0200000000000000L;
    static final long B8 = 0x0000000000000002L;
    static final long C1 = 0x0400000000000000L;
    static final long C3 = 0x0000040000000000L;
    static final long C6 = 0x0000000000040000L;
    static final long C8 = 0x0000000000000004L;
    static final long D1 = 0x0800000000000000L;
    static final long D8 = 0x0000000000000008L;
    static final long F1 = 0x2000000000000000L;
    static final long F3 = 0x0000200000000000L;
    static final long F6 = 0x0000000000200000L;
    static final long F8 = 0x0000000000000020L;
    static final long G1 = 0x4000000000000000L;
    static final long G8 = 0x0000000000000040L;
}
