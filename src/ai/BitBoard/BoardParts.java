package ai.BitBoard;

public class BoardParts {
    // information:
    public static final int NUM_OF_TILES = 64;
    public static final String FEN_STARTING_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // rows:
    public static final long FIRST_RANK = 0xFF00000000000000L;
    public static final long SECOND_RANK = 0x00FF000000000000L;
    public static final long THIRD_RANK = 0x0000FF0000000000L;
    public static final long FOURTH_RANK = 0x000000FF00000000L;
    public static final long FIFTH_RANK = 0x00000000FF000000L;
    public static final long SIXTH_RANK = 0x0000000000FF0000L;
    public static final long SEVENTH_RANK = 0x000000000000FF00L;
    public static final long EIGHTH_RANK = 0x00000000000000FFL;

    // cols
    public static final long A_FILE = 0x0101010101010101L;           // העמודה A (שמאלית)
    public static final long B_FILE = 0x0202020202020202L;           // העמודה B
    public static final long C_FILE = 0x0404040404040404L;           // העמודה C
    public static final long D_FILE = 0x0808080808080808L;           // העמודה D
    public static final long E_FILE = 0x1010101010101010L;           // העמודה E
    public static final long F_FILE = 0x2020202020202020L;           // העמודה F
    public static final long G_FILE = 0x4040404040404040L;           // העמודה G
    public static final long H_FILE = 0x8080808080808080L;           // העמודה H (ימנית)

    // חצאי מסגרות של הלוח
    public static final long BACK_LEFT_CORNER = EIGHTH_RANK | A_FILE;  // שורה שמינית ועמודה A
    public static final long BACK_RIGHT_CORNER = EIGHTH_RANK | H_FILE; // שורה שמינית ועמודה H
    public static final long FIRST_LEFT_CORNER = FIRST_RANK | A_FILE;  // שורה ראשונה ועמודה A
    public static final long FIRST_RIGHT_CORNER = FIRST_RANK | H_FILE; // שורה ראשונה ועמודה H

    // diagonals
    public static final long H8_A1_DIAGONAL = 0x8040201008040201L;    // אלכסון A8-H1
    public static final long A8_H1_DIAGONAL = 0x0102040810204080L;    // אלכסון H8-A1

    // חצאי לוח
    public static final long LEFT_HALF = 0xF0F0F0F0F0F0F0F0L;          // חצי שמאלי של הלוח
    public static final long RIGHT_HALF = 0x0F0F0F0F0F0F0F0FL;         // חצי ימני של הלוח
    public static final long BACK_HALF = 0xFFFFFFFF00000000L;          // חצי אחורי של הלוח
    public static final long FRONT_HALF = 0x00000000FFFFFFFFL;         // חצי קדמי של הלוח


    public static void printAllTiles() {
        // List of all tiles
        Tile[] tiles = {
                Tile.A1, Tile.B1, Tile.C1, Tile.D1, Tile.E1, Tile.F1, Tile.G1, Tile.H1,
                Tile.A2, Tile.B2, Tile.C2, Tile.D2, Tile.E2, Tile.F2, Tile.G2, Tile.H2,
                Tile.A3, Tile.B3, Tile.C3, Tile.D3, Tile.E3, Tile.F3, Tile.G3, Tile.H3,
                Tile.A4, Tile.B4, Tile.C4, Tile.D4, Tile.E4, Tile.F4, Tile.G4, Tile.H4,
                Tile.A5, Tile.B5, Tile.C5, Tile.D5, Tile.E5, Tile.F5, Tile.G5, Tile.H5,
                Tile.A6, Tile.B6, Tile.C6, Tile.D6, Tile.E6, Tile.F6, Tile.G6, Tile.H6,
                Tile.A7, Tile.B7, Tile.C7, Tile.D7, Tile.E7, Tile.F7, Tile.G7, Tile.H7,
                Tile.A8, Tile.B8, Tile.C8, Tile.D8, Tile.E8, Tile.F8, Tile.G8, Tile.H8
        };

        // Print each tile and its bitboard representation
        for (Tile tile : tiles) {
            System.out.println(tile.name + ": " + BitOperations.printBitboard(tile.position));
        }
    }

    public static class Tile {
        public String name;
        public long position;
        public Tile(String name, long position) {
            this.name = name;
            this.position = position;
        }
        // Squares
        public static final Tile A8 = new Tile("A8", 0x0000000000000001L);
        public static final Tile B8 = new Tile("B8", 0x0000000000000002L);
        public static final Tile C8 = new Tile("C8", 0x0000000000000004L);
        public static final Tile D8 = new Tile("D8", 0x0000000000000008L);
        public static final Tile E8 = new Tile("E8", 0x0000000000000010L);
        public static final Tile F8 = new Tile("F8", 0x0000000000000020L);
        public static final Tile G8 = new Tile("G8", 0x0000000000000040L);
        public static final Tile H8 = new Tile("H8", 0x0000000000000080L);

        public static final Tile A7 = new Tile("A7", 0x0000000000000100L);
        public static final Tile B7 = new Tile("B7", 0x0000000000000200L);
        public static final Tile C7 = new Tile("C7", 0x0000000000000400L);
        public static final Tile D7 = new Tile("D7", 0x0000000000000800L);
        public static final Tile E7 = new Tile("E7", 0x0000000000001000L);
        public static final Tile F7 = new Tile("F7", 0x0000000000002000L);
        public static final Tile G7 = new Tile("G7", 0x0000000000004000L);
        public static final Tile H7 = new Tile("H7", 0x0000000000008000L);

        public static final Tile A6 = new Tile("A6", 0x0000000000010000L);
        public static final Tile B6 = new Tile("B6", 0x0000000000020000L);
        public static final Tile C6 = new Tile("C6", 0x0000000000040000L);
        public static final Tile D6 = new Tile("D6", 0x0000000000080000L);
        public static final Tile E6 = new Tile("E6", 0x0000000000100000L);
        public static final Tile F6 = new Tile("F6", 0x0000000000200000L);
        public static final Tile G6 = new Tile("G6", 0x0000000000400000L);
        public static final Tile H6 = new Tile("H6", 0x0000000000800000L);

        public static final Tile A5 = new Tile("A5", 0x0000000001000000L);
        public static final Tile B5 = new Tile("B5", 0x0000000002000000L);
        public static final Tile C5 = new Tile("C5", 0x0000000004000000L);
        public static final Tile D5 = new Tile("D5", 0x0000000008000000L);
        public static final Tile E5 = new Tile("E5", 0x0000000010000000L);
        public static final Tile F5 = new Tile("F5", 0x0000000020000000L);
        public static final Tile G5 = new Tile("G5", 0x0000000040000000L);
        public static final Tile H5 = new Tile("H5", 0x0000000080000000L);

        public static final Tile A4 = new Tile("A4", 0x0000000100000000L);
        public static final Tile B4 = new Tile("B4", 0x0000000200000000L);
        public static final Tile C4 = new Tile("C4", 0x0000000400000000L);
        public static final Tile D4 = new Tile("D4", 0x0000000800000000L);
        public static final Tile E4 = new Tile("E4", 0x0000001000000000L);
        public static final Tile F4 = new Tile("F4", 0x0000002000000000L);
        public static final Tile G4 = new Tile("G4", 0x0000004000000000L);
        public static final Tile H4 = new Tile("H4", 0x0000008000000000L);

        public static final Tile A3 = new Tile("A3", 0x0000010000000000L);
        public static final Tile B3 = new Tile("B3", 0x0000020000000000L);
        public static final Tile C3 = new Tile("C3", 0x0000040000000000L);
        public static final Tile D3 = new Tile("D3", 0x0000080000000000L);
        public static final Tile E3 = new Tile("E3", 0x0000100000000000L);
        public static final Tile F3 = new Tile("F3", 0x0000200000000000L);
        public static final Tile G3 = new Tile("G3", 0x0000400000000000L);
        public static final Tile H3 = new Tile("H3", 0x0000800000000000L);

        public static final Tile A2 = new Tile("A2", 0x0001000000000000L);
        public static final Tile B2 = new Tile("B2", 0x0002000000000000L);
        public static final Tile C2 = new Tile("C2", 0x0004000000000000L);
        public static final Tile D2 = new Tile("D2", 0x0008000000000000L);
        public static final Tile E2 = new Tile("E2", 0x0010000000000000L);
        public static final Tile F2 = new Tile("F2", 0x0020000000000000L);
        public static final Tile G2 = new Tile("G2", 0x0040000000000000L);
        public static final Tile H2 = new Tile("H2", 0x0080000000000000L);

        public static final Tile A1 = new Tile("A1", 0x0100000000000000L);
        public static final Tile B1 = new Tile("B1", 0x0200000000000000L);
        public static final Tile C1 = new Tile("C1", 0x0400000000000000L);
        public static final Tile D1 = new Tile("D1", 0x0800000000000000L);
        public static final Tile E1 = new Tile("E1", 0x1000000000000000L);
        public static final Tile F1 = new Tile("F1", 0x2000000000000000L);
        public static final Tile G1 = new Tile("G1", 0x4000000000000000L);
        public static final Tile H1 = new Tile("H1", 0x8000000000000000L);

    }

    public static void main(String[] args) {
//        System.out.println(BitOperations.printBitboard(FIRST_RANK));
//        System.out.println(BitOperations.printBitboard(SECOND_RANK));
//        System.out.println(BitOperations.printBitboard(THIRD_RANK));
//        System.out.println(BitOperations.printBitboard(FOURTH_RANK));
//        System.out.println(BitOperations.printBitboard(FIFTH_RANK));
//        System.out.println(BitOperations.printBitboard(SIXTH_RANK));
//        System.out.println(BitOperations.printBitboard(SEVENTH_RANK));
//        System.out.println(BitOperations.printBitboard(EIGHTH_RANK));
//
//        System.out.println(BitOperations.printBitboard(A_FILE));
//        System.out.println(BitOperations.printBitboard(B_FILE));
//        System.out.println(BitOperations.printBitboard(C_FILE));
//        System.out.println(BitOperations.printBitboard(D_FILE));
//        System.out.println(BitOperations.printBitboard(E_FILE));
//        System.out.println(BitOperations.printBitboard(F_FILE));
//        System.out.println(BitOperations.printBitboard(G_FILE));
//        System.out.println(BitOperations.printBitboard(H_FILE));

        printAllTiles();
    }

}
