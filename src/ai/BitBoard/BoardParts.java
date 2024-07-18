package ai.BitBoard;

public class BoardParts {
    // rows:
    public static final long EIGHTH_RANK = 0xFF00000000000000L;      // השורה השמינית (אחורית)
    public static final long SEVENTH_RANK = 0x00FF000000000000L;     // השורה השביעית
    public static final long SIXTH_RANK = 0x0000FF0000000000L;       // השורה השישית
    public static final long FIFTH_RANK = 0x000000FF00000000L;       // השורה החמישית
    public static final long FOURTH_RANK = 0x00000000FF000000L;      // השורה הרביעית
    public static final long THIRD_RANK = 0x0000000000FF0000L;       // השורה השלישית
    public static final long SECOND_RANK = 0x000000000000FF00L;      // השורה השנייה
    public static final long FIRST_RANK = 0x00000000000000FFL;       // השורה הראשונה (קדמית)

    // cols
    public static final long A_FILE = 0x8080808080808080L;           // העמודה A (שמאלית)
    public static final long B_FILE = 0x4040404040404040L;           // העמודה B
    public static final long C_FILE = 0x2020202020202020L;           // העמודה C
    public static final long D_FILE = 0x1010101010101010L;           // העמודה D
    public static final long E_FILE = 0x0808080808080808L;           // העמודה E
    public static final long F_FILE = 0x0404040404040404L;           // העמודה F
    public static final long G_FILE = 0x0202020202020202L;           // העמודה G
    public static final long H_FILE = 0x0101010101010101L;           // העמודה H (ימנית)

    // חצאי מסגרות של הלוח
    public static final long BACK_LEFT_CORNER = EIGHTH_RANK | A_FILE;  // שורה שמינית ועמודה A
    public static final long BACK_RIGHT_CORNER = EIGHTH_RANK | H_FILE; // שורה שמינית ועמודה H
    public static final long FIRST_LEFT_CORNER = FIRST_RANK | A_FILE;  // שורה ראשונה ועמודה A
    public static final long FIRST_RIGHT_CORNER = FIRST_RANK | H_FILE; // שורה ראשונה ועמודה H

    // diagonals
    public static final long H8_A1_DIAGONAL = 0x8040201008040201L;    // אלכסון A8-H1
    public static final long A8_H1_DIAGONAL = 0x0102040810204080L;    // אלכסון H8-A1

    // Squares (example for a few squares)
    public static final long A8 = 0x0000000000000001L;
    public static final long B8 = 0x0000000000000002L;
    public static final long C8 = 0x0000000000000004L;
    public static final long D8 = 0x0000000000000008L;
    public static final long E8 = 0x0000000000000010L;
    public static final long F8 = 0x0000000000000020L;
    public static final long G8 = 0x0000000000000040L;
    public static final long H8 = 0x0000000000000080L;
    public static final long A7 = 0x0000000000000100L;
    public static final long B7 = 0x0000000000000200L;
    public static final long C7 = 0x0000000000000400L;
    public static final long D7 = 0x0000000000000800L;
    public static final long E7 = 0x0000000000001000L;
    public static final long F7 = 0x0000000000002000L;
    public static final long G7 = 0x0000000000004000L;
    public static final long H7 = 0x0000000000008000L;
    public static final long A6 = 0x0000000000010000L;
    public static final long B6 = 0x0000000000020000L;
    public static final long C6 = 0x0000000000040000L;
    public static final long D6 = 0x0000000000080000L;
    public static final long E6 = 0x0000000000100000L;
    public static final long F6 = 0x0000000000200000L;
    public static final long G6 = 0x0000000000400000L;
    public static final long H6 = 0x0000000000800000L;
    public static final long A5 = 0x0000000001000000L;
    public static final long B5 = 0x0000000002000000L;
    public static final long C5 = 0x0000000004000000L;
    public static final long D5 = 0x0000000008000000L;
    public static final long E5 = 0x0000000010000000L;
    public static final long F5 = 0x0000000020000000L;
    public static final long G5 = 0x0000000040000000L;
    public static final long H5 = 0x0000000080000000L;
    public static final long A4 = 0x0000000100000000L;
    public static final long B4 = 0x0000000200000000L;
    public static final long C4 = 0x0000000400000000L;
    public static final long D4 = 0x0000000800000000L;
    public static final long E4 = 0x0000001000000000L;
    public static final long F4 = 0x0000002000000000L;
    public static final long G4 = 0x0000004000000000L;
    public static final long H4 = 0x0000008000000000L;
    public static final long A3 = 0x0000010000000000L;
    public static final long B3 = 0x0000020000000000L;
    public static final long C3 = 0x0000040000000000L;
    public static final long D3 = 0x0000080000000000L;
    public static final long E3 = 0x0000100000000000L;
    public static final long F3 = 0x0000200000000000L;
    public static final long G3 = 0x0000400000000000L;
    public static final long H3 = 0x0000800000000000L;
    public static final long A2 = 0x0001000000000000L;
    public static final long B2 = 0x0002000000000000L;
    public static final long C2 = 0x0004000000000000L;
    public static final long D2 = 0x0008000000000000L;
    public static final long E2 = 0x0010000000000000L;
    public static final long F2 = 0x0020000000000000L;
    public static final long G2 = 0x0040000000000000L;
    public static final long H2 = 0x0080000000000000L;
    public static final long A1 = 0x0100000000000000L;
    public static final long B1 = 0x0200000000000000L;
    public static final long C1 = 0x0400000000000000L;
    public static final long D1 = 0x0800000000000000L;
    public static final long E1 = 0x1000000000000000L;
    public static final long F1 = 0x2000000000000000L;
    public static final long G1 = 0x4000000000000000L;
    public static final long H1 = 0x8000000000000000L;


    // חצאי לוח
    public static final long LEFT_HALF = 0xF0F0F0F0F0F0F0F0L;          // חצי שמאלי של הלוח
    public static final long RIGHT_HALF = 0x0F0F0F0F0F0F0F0FL;         // חצי ימני של הלוח
    public static final long BACK_HALF = 0xFFFFFFFF00000000L;          // חצי אחורי של הלוח
    public static final long FRONT_HALF = 0x00000000FFFFFFFFL;         // חצי קדמי של הלוח





}
