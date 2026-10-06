package arena;

import ai.board.BoardRules;
import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.variant.CastlingRule;
import ai.variant.Variant;
import ai.variant.Variants;
import ai.variant.WinCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A made variant written as a Fairy-Stockfish config section ({@code variants.ini}), so Fairy-Stockfish
 * can play it as a yardstick. The same mapping as {@code made_config} in tools/variant_oracle.py,
 * which {@code VariantOracleTest} checks move for move: the variant inherits a built-in game, drops
 * the chess pieces it does not have, and adds its invented pieces as {@code customPieceN = letter:betza}.
 *
 * <p>A variant whose goals are one of the four presets inherits that game (chess, antichess,
 * kingofthehill, 3check); any other mix inherits chess and states its rules as options: a squares
 * goal as {@code flagPiece}/{@code flagRegion}, N checks as {@code checkCounting} with the count in
 * the start FEN, capturing all of one type as {@code extinctionValue}/{@code extinctionPieceTypes},
 * and the stalemate, move limit and castling settings as {@code stalemateValue}, {@code nMoveRule},
 * {@code castlingKingsideFile}/{@code castlingQueensideFile}/{@code castlingRookPieces} (the sides
 * through the start FEN's castling rights).
 *
 * <p>Anything else gets no config, and {@link #refusal} says why: Fairy-Stockfish does not play it,
 * and the variant is "our engine only". That is a board other than 8x8, a king or pawn unlike the
 * base game's (Fairy-Stockfish keeps its own), another royal piece or two royal pieces a side, a "first
 * move only" atom on another piece (Betza {@code i}, where the two engines differ), checkmate turned
 * off, the bare-royal goal, repetition turned off, castling through attacked squares or with the partner
 * landing outside, and goals that cannot be said together.
 */
public final class FairyConfig {

    /** Chess pieces Fairy-Stockfish has built in: letter -> its name in a config. */
    private static final Map<Character, String> BUILT_IN = Map.of('Q', "queen", 'R', "rook", 'B', "bishop", 'N', "knight");

    private FairyConfig() {}

    /** A config, or the reason there is none. */
    private record Result(String config, String reason) {}

    /** The built-in game {@code variant}'s goals and settings equal, if any. */
    static Optional<Variant> base(Variant variant) {
        return variant.legacyGoal().map(g -> switch (g) {
            case CHECKMATE -> Variants.CHESS;
            case LOSE_EVERYTHING -> Variants.ANTICHESS;
            case KING_OF_THE_HILL -> variant.goals().equals(Variants.KING_OF_THE_HILL.goals()) ? Variants.KING_OF_THE_HILL : null;
            case CHECKS -> variant.checksToWin() == 3 ? Variants.THREE_CHECK : null;
        });
    }

    /** The config section for {@code variant}, named by its id; empty if it cannot be written ({@link #refusal}). */
    public static Optional<String> of(Variant variant) {
        return Optional.ofNullable(write(variant).config());
    }

    /** Why Fairy-Stockfish cannot play {@code variant}, in a sentence for people; empty when it can. */
    public static Optional<String> refusal(Variant variant) {
        if (Variants.ALL.contains(variant)) {
            return Optional.empty();
        }
        return Optional.ofNullable(write(variant).reason());
    }

    private static Result no(String reason) {
        return new Result(null, reason);
    }

    private static Result write(Variant variant) {
        if (!variant.grid().equals(Grid.CHESS)) {
            return no("Fairy-Stockfish is given only 8x8 boards");
        }
        PieceType king = piece(variant, 'K');
        PieceType pawn = piece(variant, 'P');
        boolean antichess = variant.has(WinCondition.Kind.LOSE_EVERYTHING);
        Variant family = antichess ? Variants.ANTICHESS : Variants.CHESS;
        if (king == null || pawn == null) {
            return no("it has no king or no pawn, which Fairy-Stockfish keeps as its own");
        }
        if (!sameMoves(king, piece(family, 'K')) || !sameMoves(pawn, piece(family, 'P'))) {
            return no("its king or pawn differs from " + family.name() + "'s, and Fairy-Stockfish keeps its own");
        }
        if (variant.pieces().stream().anyMatch(p -> p.royal() && p.letter() != 'K')) {
            return no("a piece other than the king is royal");
        }
        if (!antichess && countOnStart(variant, 'K') > 1) {
            return no("a side has more than one king");
        }
        if (!variant.repetition()) {
            return no("repetition is turned off");
        }
        List<String> options = new ArrayList<>();
        Optional<Variant> preset = base(variant);
        String reason = preset.isPresent() ? null : goalOptions(variant, antichess, options);
        if (reason != null) {
            return no(reason);
        }
        reason = castlingOptions(variant, options);
        if (reason != null) {
            return no(reason);
        }
        Variant base = preset.orElse(family);
        String baseName = FairyStockfish.variantName(base).orElseThrow();
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
                return no(p.name() + " has a first-move-only move, where the two engines differ");
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
        lines.add("castling = " + variant.castling().enabled());
        lines.add("mustCapture = " + variant.forcedCapture());
        lines.addAll(options);
        lines.add("startFen = " + startFen(variant));
        return new Result(String.join("\n", lines) + "\n", null);
    }

    /** The options for goals that are no preset, on top of chess or antichess; or why there are none. */
    private static String goalOptions(Variant variant, boolean antichess, List<String> options) {
        if (antichess) {
            if (variant.goals().size() > 1) {
                return "losing everything is mixed with other goals";
            }
            if (variant.moveLimit() != 50) {
                options.add("nMoveRule = " + variant.moveLimit());
            }
            return null; // antichess: having no move wins whatever the stalemate rule says
        }
        if (!variant.has(WinCondition.Kind.CHECKMATE)) {
            return "checkmate is turned off";
        }
        if (variant.royalMode() == Variant.RoyalMode.LAST_STANDING && countOnStart(variant, 'K') > 1) {
            return "the last-standing royal rule";
        }
        boolean flag = false;
        boolean extinction = false;
        for (WinCondition g : variant.goals()) {
            switch (g.kind()) {
                case CHECKMATE -> { }
                case CHECKS -> options.add("checkCounting = true");
                case REACH_SQUARES -> {
                    if (flag) {
                        return "it has two squares goals";
                    }
                    flag = true;
                    String types = g.pieces().isEmpty() ? "K" : g.pieces();
                    if (types.length() != 1) {
                        return "a squares goal counts more than one piece type";
                    }
                    String squares = String.join(" ", g.squares());
                    options.add("flagPiece = " + types.toLowerCase());
                    options.add("flagRegionWhite = " + squares);
                    options.add("flagRegionBlack = " + squares);
                }
                case CAPTURE_ALL_OF -> {
                    if (extinction || g.pieces().length() != 1) {
                        return "capturing all of more than one piece type";
                    }
                    extinction = true;
                    options.add("extinctionValue = loss");
                    options.add("extinctionPieceTypes = " + g.pieces().toLowerCase());
                }
                case BARE_ROYAL -> {
                    return "the bare-royal goal";
                }
                case LOSE_EVERYTHING -> {
                    return "losing everything is mixed with other goals";
                }
            }
        }
        if (variant.stalemate() != Variant.Stalemate.DRAW) {
            options.add("stalemateValue = " + variant.stalemate().name().toLowerCase());
        }
        if (variant.moveLimit() != 50) {
            options.add("nMoveRule = " + variant.moveLimit());
        }
        return null;
    }

    /** The castling options beyond chess's; or why castling cannot be said. */
    private static String castlingOptions(Variant variant, List<String> options) {
        CastlingRule rule = variant.castling();
        if (!rule.enabled()) {
            return null;
        }
        if (!rule.safePassage()) {
            return "castling through attacked squares";
        }
        if (rule.partner() != CastlingRule.Partner.INSIDE) {
            return "the castling partner lands on the outside";
        }
        for (PieceType p : variant.pieces()) {
            if (p.castling() == PieceType.Castling.KING && p.letter() != 'K') {
                return p.name() + " castles, and Fairy-Stockfish castles only with the king";
            }
        }
        List<BoardRules.Castling> castlings = BoardRules.of(variant).castlings();
        for (boolean kingSide : new boolean[]{true, false}) {
            java.util.Set<Integer> files = castlings.stream().filter(c -> c.kingSide() == kingSide)
                    .map(c -> c.kingTo() % 8).collect(Collectors.toSet());
            if (files.size() > 1) {
                return "the king lands on different files for the two sides";
            }
            int chess = kingSide ? 6 : 2;
            if (files.size() == 1 && files.iterator().next() != chess) {
                options.add((kingSide ? "castlingKingsideFile = " : "castlingQueensideFile = ")
                        + (char) ('a' + files.iterator().next()));
            }
        }
        String rooks = variant.pieces().stream().filter(p -> p.castling() == PieceType.Castling.ROOK)
                .map(p -> String.valueOf(Character.toLowerCase(p.letter()))).collect(Collectors.joining());
        if (!rooks.equals("r") && !rooks.isEmpty()) {
            options.add("castlingRookPieces = " + rooks);
        }
        return null;
    }

    /**
     * The start position as Fairy-Stockfish reads it: only the castling rights the variant's sides
     * allow, and with a checks goal the checks still to give ("3+3") before the move counters.
     */
    static String startFen(Variant variant) {
        String[] f = variant.startFen().trim().split("\\s+");
        List<String> out = new ArrayList<>(List.of(f));
        if (out.size() > 2 && variant.castling().enabled() && variant.castling().sides() != CastlingRule.Sides.BOTH) {
            String keep = variant.castling().sides() == CastlingRule.Sides.KING_SIDE ? "Kk" : "Qq";
            String rights = out.get(2).chars().filter(c -> keep.indexOf(c) >= 0)
                    .mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining());
            out.set(2, rights.isEmpty() ? "-" : rights);
        }
        int checks = variant.checksToWin();
        if (checks > 0 && base(variant).isEmpty() && out.size() >= 4 && out.stream().noneMatch(s -> s.contains("+"))) {
            out.add(4, checks + "+" + checks);
        }
        return String.join(" ", out);
    }

    private static long countOnStart(Variant variant, char letter) {
        String placement = variant.startPlacement();
        return placement.chars().filter(c -> c == letter).count();
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
