package ai.variant;

import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.piece.StandardPieces;

import java.util.List;

/** Made-up variants for tests (Phase 6 R5): what the owner could build in the designer. */
public final class TestVariants {

    private TestVariants() {}

    /** The Amazon: a queen that also jumps like a knight. */
    public static final PieceType AMAZON = PieceType.of("Amazon", 'A', 1200, Betza.parse("QN").toArray(ai.piece.Atom[]::new));

    /** Chess with an Amazon in place of the queen; pawns promote to it. */
    public static final Variant AMAZON_CHESS = new Variant("amazon-chess", "Amazon chess",
            List.of(StandardPieces.KING, AMAZON, StandardPieces.ROOK, StandardPieces.BISHOP, StandardPieces.KNIGHT,
                    pawnPromotingTo('A', 'R', 'B', 'N')),
            Grid.CHESS, "rnbakbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBAKBNR w KQkq - 0 1", Variant.Goal.CHECKMATE, 0, false, true);

    public static PieceType pawnPromotingTo(Character... letters) {
        PieceType p = StandardPieces.PAWN;
        return new PieceType(p.name(), p.letter(), p.atoms(), p.royal(), List.of(letters), p.enPassant(), p.castling(), p.value());
    }
}
