package ai.piece;

import ai.piece.Atom.Mode;
import ai.piece.Atom.Symmetry;

import java.util.List;

/** The six chess pieces, written as data (Phase 6 R2). Values are the textbook ones. */
public final class StandardPieces {

    private StandardPieces() {}

    public static final PieceType KING = new PieceType("King", 'K',
            List.of(Atom.leap(1, 0, Symmetry.ALL, Mode.BOTH), Atom.leap(1, 1, Symmetry.ALL, Mode.BOTH)),
            true, List.of(), false, PieceType.Castling.KING, 0);

    public static final PieceType QUEEN = PieceType.of("Queen", 'Q', 900,
            Atom.slide(1, 0, Symmetry.ALL, Mode.BOTH), Atom.slide(1, 1, Symmetry.ALL, Mode.BOTH));

    public static final PieceType ROOK = new PieceType("Rook", 'R',
            List.of(Atom.slide(1, 0, Symmetry.ALL, Mode.BOTH)),
            false, List.of(), false, PieceType.Castling.ROOK, 500);

    public static final PieceType BISHOP = PieceType.of("Bishop", 'B', 330,
            Atom.slide(1, 1, Symmetry.ALL, Mode.BOTH));

    public static final PieceType KNIGHT = PieceType.of("Knight", 'N', 320,
            Atom.leap(2, 1, Symmetry.ALL, Mode.BOTH));

    /** One step forward, two from its first square, captures one step diagonally forward. */
    public static final PieceType PAWN = new PieceType("Pawn", 'P',
            List.of(Atom.slide(1, 0, Symmetry.ONE, Mode.MOVE).withRange(1),
                    Atom.slide(1, 0, Symmetry.ONE, Mode.MOVE).withRange(2).firstMove(),
                    Atom.leap(1, 1, Symmetry.SIDEWAYS, Mode.CAPTURE)),
            false, List.of('Q', 'R', 'B', 'N'), true, PieceType.Castling.NONE, 100);

    /** In the order the engine numbers them: king 1 ... pawn 6. */
    public static final List<PieceType> ALL = List.of(KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN);
}
