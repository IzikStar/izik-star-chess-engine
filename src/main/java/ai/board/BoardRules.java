package ai.board;

import ai.piece.Atom;
import ai.piece.CompiledPiece;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.variant.Variant;
import ai.variant.Variants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a {@link GenericBoard} plays by (Phase 6 R3, R4): a {@link Variant}'s piece types on its grid,
 * compiled once for both players, the castling moves the start position allows, the variant's rule
 * switches, and the hashing keys. Immutable and shared by every position of every game played with it.
 *
 * <p>Castling is read from the start position: for each player, the piece with the {@code KING} role
 * and the outermost piece with the {@code ROOK} role on each side of it on its row give one castling
 * each. As in chess, the king ends on the second file from that edge (g or c) and the rook next to it
 * on the inside (f or d).
 */
public final class BoardRules {

    private static final Map<Variant, BoardRules> COMPILED = new ConcurrentHashMap<>();

    /** Standard chess. */
    public static final BoardRules CHESS = of(Variants.CHESS);

    /** The rules of {@code variant}, compiled once and shared. */
    public static BoardRules of(Variant variant) {
        return COMPILED.computeIfAbsent(variant, BoardRules::new);
    }

    /**
     * One castling move.
     *
     * @param right     bit of this castling in a position's castling rights
     * @param letter    its FEN letter (K, Q for player 0; k, q for player 1)
     * @param empty     squares that must be empty (besides the king's and the rook's own)
     * @param safe      squares the opponent must not attack: where the king starts, crosses and ends
     */
    public record Castling(int player, int right, char letter, boolean kingSide, int kingFrom, int kingTo,
                           int rookFrom, int rookTo, long empty, long safe) {}

    final Variant variant;
    final List<PieceType> types;
    final Grid grid;
    /** [player][type]. */
    final CompiledPiece[][] compiled;
    final boolean[] royal;
    final boolean[] enPassant;
    final boolean[] firstMoveAtoms;
    /** [type]: the type indices it may promote to, in the order listed; empty when it never promotes. */
    final int[][] promotesTo;
    /** [player]: the row its pieces promote on. */
    final long[] promotionRow;
    final List<Castling> castlings;
    /** [player][type]: where the start position has that piece; first-move atoms count there. */
    final long[][] startSquares;
    /** [player][type][square]. */
    final long[][][] zobrist;
    final long blackToMove;
    final long[] rightKeys;
    final long[] castledKeys = new long[2];
    final long[] epKeys;
    /** Victim values for move ordering (value / 10), and the king's capture "value". */
    final int[] orderValue;
    /** Rough values for MVV-LVA and for skipping losing captures (value / 100; a royal piece 1000). */
    final int[] roughValue;
    final String startPlacement;
    /** The type with the {@code ROOK} castling role, or -1. */
    final int rookType;
    final Variant.Goal goal;
    final int checksToWin;
    final boolean forcedCapture;
    /** With {@link Variant.Goal#KING_OF_THE_HILL}: the centre squares; otherwise 0. */
    final long hill;
    final long checksKey;

    private BoardRules(Variant variant) {
        this.variant = variant;
        List<PieceType> types = variant.pieces();
        this.types = types;
        this.grid = variant.grid();
        this.startPlacement = variant.startPlacement();
        this.goal = variant.goal();
        this.checksToWin = variant.checksToWin();
        this.forcedCapture = variant.forcedCapture();
        int n = types.size();
        compiled = new CompiledPiece[2][n];
        royal = new boolean[n];
        enPassant = new boolean[n];
        firstMoveAtoms = new boolean[n];
        promotesTo = new int[n][];
        orderValue = new int[n];
        roughValue = new int[n];
        for (int t = 0; t < n; t++) {
            PieceType type = types.get(t);
            for (int player = 0; player < 2; player++) {
                compiled[player][t] = new CompiledPiece(type, grid, player);
            }
            royal[t] = type.royal();
            enPassant[t] = type.enPassant();
            firstMoveAtoms[t] = type.atoms().stream().anyMatch(Atom::firstMoveOnly);
            orderValue[t] = type.value() / 10;
            roughValue[t] = type.royal() ? 1000 : Math.max(1, Math.round(type.value() / 100f));
        }
        for (int t = 0; t < n; t++) {
            List<Character> letters = types.get(t).promotesTo();
            promotesTo[t] = new int[letters.size()];
            for (int i = 0; i < letters.size(); i++) {
                promotesTo[t][i] = typeOf(letters.get(i));
            }
        }
        long top = 0;
        long bottom = 0;
        for (int col = 0; col < grid.width(); col++) {
            top |= 1L << grid.square(0, col);
            bottom |= 1L << grid.square(grid.height() - 1, col);
        }
        promotionRow = new long[]{top, bottom};
        startSquares = new long[2][n];
        int[] placement = parsePlacement(startPlacement);
        for (int sq = 0; sq < placement.length; sq++) {
            if (placement[sq] >= 0) {
                startSquares[placement[sq] / n][placement[sq] % n] |= 1L << sq;
            }
        }
        castlings = variant.castling() ? findCastlings(placement) : List.of();
        long centre = 0;
        if (goal == Variant.Goal.KING_OF_THE_HILL) {
            for (int row = (grid.height() - 1) / 2; row <= grid.height() / 2; row++) {
                for (int col = (grid.width() - 1) / 2; col <= grid.width() / 2; col++) {
                    centre |= 1L << grid.square(row, col);
                }
            }
        }
        hill = centre;
        int rook = -1;
        for (int t = 0; t < n; t++) {
            if (types.get(t).castling() == PieceType.Castling.ROOK) {
                rook = t;
            }
        }
        rookType = rook;

        Random random = new Random(0);
        zobrist = new long[2][n][grid.squares()];
        for (long[][] player : zobrist) {
            for (long[] type : player) {
                for (int sq = 0; sq < type.length; sq++) {
                    type[sq] = random.nextLong();
                }
            }
        }
        blackToMove = random.nextLong();
        rightKeys = new long[castlings.size()];
        for (int i = 0; i < rightKeys.length; i++) {
            rightKeys[i] = random.nextLong();
        }
        castledKeys[0] = random.nextLong();
        castledKeys[1] = random.nextLong();
        epKeys = new long[grid.squares()];
        for (int sq = 0; sq < epKeys.length; sq++) {
            epKeys[sq] = random.nextLong();
        }
        checksKey = random.nextLong();
    }

    public Variant variant() {
        return variant;
    }

    public List<PieceType> types() {
        return types;
    }

    public Grid grid() {
        return grid;
    }

    public List<Castling> castlings() {
        return castlings;
    }

    /** The type with this letter (either case), or -1. */
    int typeOf(char letter) {
        char upper = Character.toUpperCase(letter);
        for (int t = 0; t < types.size(); t++) {
            if (types.get(t).letter() == upper) {
                return t;
            }
        }
        return -1;
    }

    /** FEN placement -> per square {@code player * types + type}, or -1 for empty. */
    int[] parsePlacement(String placement) {
        int[] squares = new int[grid.squares()];
        java.util.Arrays.fill(squares, -1);
        String[] rows = placement.split("/");
        if (rows.length != grid.height()) {
            throw new IllegalArgumentException("expected " + grid.height() + " rows: " + placement);
        }
        for (int row = 0; row < rows.length; row++) {
            int col = 0;
            String text = rows[row];
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (Character.isDigit(ch)) {
                    int run = ch - '0';
                    while (i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) {
                        run = run * 10 + text.charAt(++i) - '0';
                    }
                    col += run;
                    continue;
                }
                int type = typeOf(ch);
                if (type < 0 || col >= grid.width()) {
                    throw new IllegalArgumentException("bad placement " + placement);
                }
                int player = Character.isUpperCase(ch) ? 0 : 1;
                squares[grid.square(row, col++)] = player * types.size() + type;
            }
            if (col != grid.width()) {
                throw new IllegalArgumentException("row " + (row + 1) + " has " + col + " squares: " + placement);
            }
        }
        return squares;
    }

    private List<Castling> findCastlings(int[] placement) {
        List<Castling> found = new ArrayList<>();
        int n = types.size();
        for (int player = 0; player < 2; player++) {
            for (int kingSide = 1; kingSide >= 0; kingSide--) {
                for (int sq = 0; sq < placement.length; sq++) {
                    if (placement[sq] < 0 || placement[sq] / n != player
                            || types.get(placement[sq] % n).castling() != PieceType.Castling.KING) {
                        continue;
                    }
                    int row = grid.row(sq);
                    int kingCol = grid.col(sq);
                    // the rook furthest toward that side
                    int rookCol = -1;
                    for (int col = kingSide == 1 ? grid.width() - 1 : 0;
                         kingSide == 1 ? col > kingCol : col < kingCol; col += kingSide == 1 ? -1 : 1) {
                        int p = placement[grid.square(row, col)];
                        if (p >= 0 && p / n == player && types.get(p % n).castling() == PieceType.Castling.ROOK) {
                            rookCol = col;
                            break;
                        }
                    }
                    if (rookCol < 0) {
                        continue;
                    }
                    int kingToCol = kingSide == 1 ? grid.width() - 2 : 2;
                    int rookToCol = kingSide == 1 ? kingToCol - 1 : kingToCol + 1;
                    long empty = 0;
                    long safe = 0;
                    for (int col = Math.min(kingCol, Math.min(rookCol, Math.min(kingToCol, rookToCol)));
                         col <= Math.max(kingCol, Math.max(rookCol, Math.max(kingToCol, rookToCol))); col++) {
                        if (col != kingCol && col != rookCol && (between(col, kingCol, rookCol)
                                || between(col, kingCol, kingToCol) || between(col, rookCol, rookToCol))) {
                            empty |= 1L << grid.square(row, col);
                        }
                        if (between(col, kingCol, kingToCol)) {
                            safe |= 1L << grid.square(row, col);
                        }
                    }
                    char letter = kingSide == 1 ? 'K' : 'Q';
                    found.add(new Castling(player, found.size(), player == 0 ? letter : Character.toLowerCase(letter),
                            kingSide == 1, sq, grid.square(row, kingToCol), grid.square(row, rookCol),
                            grid.square(row, rookToCol), empty, safe));
                }
            }
        }
        return List.copyOf(found);
    }

    private static boolean between(int x, int a, int b) {
        return x >= Math.min(a, b) && x <= Math.max(a, b);
    }
}
