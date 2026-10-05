package ai.variant;

import ai.piece.Grid;
import ai.piece.PieceType;
import ai.piece.StandardPieces;

import java.util.List;
import java.util.Optional;

/** The variants that come with the program; the owner's own are saved as {@link VariantJson}. */
public final class Variants {

    private Variants() {}

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static final Variant CHESS = new Variant("chess", "Chess", StandardPieces.ALL, Grid.CHESS, START,
            Variant.Goal.CHECKMATE, 0, false, true);

    /**
     * Lose all your pieces (or be stalemated) to win; capturing is compulsory, the king is an
     * ordinary piece a pawn may also promote to, and there is no castling.
     */
    public static final Variant ANTICHESS = new Variant("antichess", "Antichess", antichessPieces(), Grid.CHESS,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", Variant.Goal.LOSE_EVERYTHING, 0, true, false);

    /** Chess, and bringing your king to d4, e4, d5 or e5 also wins. */
    public static final Variant KING_OF_THE_HILL = new Variant("king-of-the-hill", "King of the Hill",
            StandardPieces.ALL, Grid.CHESS, START, Variant.Goal.KING_OF_THE_HILL, 0, false, true);

    /** Chess, and the third check wins. */
    public static final Variant THREE_CHECK = new Variant("three-check", "Three-check", StandardPieces.ALL,
            Grid.CHESS, START, Variant.Goal.CHECKS, 3, false, true);

    public static final List<Variant> ALL = List.of(CHESS, ANTICHESS, KING_OF_THE_HILL, THREE_CHECK);

    public static Optional<Variant> byId(String id) {
        return ALL.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    private static List<PieceType> antichessPieces() {
        PieceType k = StandardPieces.KING;
        PieceType king = new PieceType(k.name(), k.letter(), k.atoms(), false, List.of(), false,
                PieceType.Castling.NONE, k.value());
        PieceType p = StandardPieces.PAWN;
        PieceType pawn = new PieceType(p.name(), p.letter(), p.atoms(), false, List.of('Q', 'R', 'B', 'N', 'K'),
                true, PieceType.Castling.NONE, p.value());
        return List.of(king, StandardPieces.QUEEN, StandardPieces.ROOK, StandardPieces.BISHOP, StandardPieces.KNIGHT,
                pawn);
    }
}
