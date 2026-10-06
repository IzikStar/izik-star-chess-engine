package arena;

import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.variant.Variant;
import ai.variant.Variants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A made variant written as a Fairy-Stockfish config section ({@code variants.ini}), so Fairy-Stockfish
 * can play it as a yardstick. The same mapping as {@code made_config} in tools/variant_oracle.py,
 * which {@code VariantOracleTest} checks move for move: the variant inherits a built-in game by its
 * goal, drops the chess pieces it does not have, and adds its invented pieces as
 * {@code customPieceN = letter:betza}.
 *
 * <p>Only what that mapping covers is written; anything else gets no config, and Fairy-Stockfish
 * does not play it: a board other than 8x8, a king or pawn unlike the base game's (Fairy-Stockfish
 * keeps its own), a "first move only" atom on another piece (Betza {@code i}, where the two engines
 * differ), or a check count other than three.
 */
public final class FairyConfig {

    /** Chess pieces Fairy-Stockfish has built in: letter -> its name in a config. */
    private static final Map<Character, String> BUILT_IN = Map.of('Q', "queen", 'R', "rook", 'B', "bishop", 'N', "knight");

    private FairyConfig() {}

    /** The built-in game {@code variant} is written on top of, if any. */
    static Optional<Variant> base(Variant variant) {
        return switch (variant.goal()) {
            case CHECKMATE -> Optional.of(Variants.CHESS);
            case LOSE_EVERYTHING -> Optional.of(Variants.ANTICHESS);
            case KING_OF_THE_HILL -> Optional.of(Variants.KING_OF_THE_HILL);
            case CHECKS -> variant.checksToWin() == 3 ? Optional.of(Variants.THREE_CHECK) : Optional.empty();
        };
    }

    /** The config section for {@code variant}, named by its id; empty if it cannot be written. */
    public static Optional<String> of(Variant variant) {
        if (!variant.grid().equals(Grid.CHESS)) {
            return Optional.empty();
        }
        Optional<Variant> base = base(variant);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        PieceType king = piece(variant, 'K'), pawn = piece(variant, 'P');
        if (king == null || pawn == null || !sameMoves(king, piece(base.get(), 'K'))
                || !sameMoves(pawn, piece(base.get(), 'P'))) {
            return Optional.empty();
        }
        String baseName = FairyStockfish.variantName(base.get()).orElseThrow();
        List<String> lines = new ArrayList<>();
        lines.add("[" + variant.id() + ":" + baseName + "]");
        for (Map.Entry<Character, String> builtIn : new java.util.TreeMap<>(BUILT_IN).entrySet()) {
            PieceType own = piece(variant, builtIn.getKey());
            if (own == null || !Betza.write(own.atoms()).equals(Betza.write(piece(Variants.CHESS, builtIn.getKey()).atoms()))) {
                lines.add(builtIn.getValue() + " = -");
            }
        }
        int custom = 0;
        for (PieceType p : variant.pieces()) {
            char letter = p.letter();
            if (letter == 'K' || letter == 'P') {
                continue;
            }
            if (p.atoms().stream().anyMatch(a -> a.firstMoveOnly())) {
                return Optional.empty();
            }
            PieceType chess = BUILT_IN.containsKey(letter) ? piece(Variants.CHESS, letter) : null;
            if (chess != null && Betza.write(chess.atoms()).equals(Betza.write(p.atoms()))) {
                continue;
            }
            custom++;
            lines.add("customPiece" + custom + " = " + Character.toLowerCase(letter) + ":" + Betza.write(p.atoms()));
        }
        lines.add("promotionPieceTypes = " + pawn.promotesTo().stream()
                .map(c -> String.valueOf(Character.toLowerCase(c))).collect(Collectors.joining()));
        lines.add("castling = " + variant.castling());
        lines.add("mustCapture = " + variant.forcedCapture());
        lines.add("startFen = " + variant.startFen());
        return Optional.of(String.join("\n", lines) + "\n");
    }

    private static PieceType piece(Variant variant, char letter) {
        return variant.pieces().stream().filter(p -> p.letter() == letter).findFirst().orElse(null);
    }

    /** The same moves and rule roles; the value and the promotion set may differ. */
    private static boolean sameMoves(PieceType a, PieceType b) {
        return Betza.write(a.atoms()).equals(Betza.write(b.atoms())) && a.royal() == b.royal() && a.enPassant() == b.enPassant()
                && a.castling() == b.castling();
    }
}
