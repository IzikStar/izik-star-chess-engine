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

    /** Checkmate wins; stalemate, threefold repetition and the 50-move rule draw; castling as in chess. */
    public static final Variant CHESS = preset("chess", "Chess", StandardPieces.ALL, START,
            List.of(WinCondition.checkmate()), Variant.Stalemate.DRAW, false, CastlingRule.CHESS);

    /**
     * Lose all your pieces (or be stalemated) to win; capturing is compulsory, the king is an
     * ordinary piece a pawn may also promote to, and there is no castling.
     */
    public static final Variant ANTICHESS = preset("antichess", "Antichess", antichessPieces(),
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", List.of(WinCondition.loseEverything()),
            Variant.Stalemate.WIN, true, CastlingRule.NONE);

    /** Chess, and bringing your king to d4, e4, d5 or e5 also wins. */
    public static final Variant KING_OF_THE_HILL = preset("king-of-the-hill", "King of the Hill",
            StandardPieces.ALL, START, List.of(WinCondition.checkmate(),
                    WinCondition.reach(List.of("d5", "e5", "d4", "e4"), "")), Variant.Stalemate.DRAW, false, CastlingRule.CHESS);

    /** Chess, and the third check wins. */
    public static final Variant THREE_CHECK = preset("three-check", "Three-check", StandardPieces.ALL, START,
            List.of(WinCondition.checkmate(), WinCondition.checks(3)), Variant.Stalemate.DRAW, false, CastlingRule.CHESS);

    public static final List<Variant> ALL = List.of(CHESS, ANTICHESS, KING_OF_THE_HILL, THREE_CHECK);

    public static Optional<Variant> byId(String id) {
        return ALL.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    /** A built-in game on the 8x8 board: every royal piece must stay safe, repetition and 50 moves draw. */
    private static Variant preset(String id, String name, List<PieceType> pieces, String start, List<WinCondition> goals,
                                  Variant.Stalemate stalemate, boolean forcedCapture, CastlingRule castling) {
        return new Variant(id, name, pieces, Grid.CHESS, start, goals, Variant.RoyalMode.ALL_SAFE, stalemate, true, 50,
                forcedCapture, castling);
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
