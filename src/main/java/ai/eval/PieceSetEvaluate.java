package ai.eval;

import ai.board.Board;
import ai.board.Outcome;
import ai.board.PieceBoard;
import ai.piece.PieceType;
import ai.variant.Variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An evaluation for any variant, built from its piece set (Phase 6 R4c). For each piece type:
 * <ul>
 *   <li>{@code material.<piece>}: what one piece is worth;</li>
 *   <li>{@code mobility.<piece>}: what each square it can go to is worth;</li>
 *   <li>{@code square.<piece>.<square>}: what it is worth on that square, the square seen from its
 *       owner's side (player 1 sees the board turned half a circle, as {@link ai.piece.Grid} does).</li>
 * </ul>
 * The score is the sum for the player to move minus the opponent's. Material starts at the piece's
 * value, everything else at 0; in antichess, where material is something to get rid of, every
 * weight starts at 0, so evolution starts from nothing (owner's decision D2).
 */
public final class PieceSetEvaluate implements Evaluator {

    private static final Map<Variant, ParamSchema> SCHEMAS = new ConcurrentHashMap<>();

    private final Variant variant;
    private final ParamVector params;
    private final int types;
    private final int squares;
    private final int[] material;
    private final int[] mobility;
    /** [type * squares + square, from the owner's side]. */
    private final int[] square;
    private final boolean usesMobility;

    public PieceSetEvaluate(Variant variant, ParamVector params) {
        if (params.size() != schema(variant).size()) {
            throw new IllegalArgumentException("parameters of another schema for " + variant.id());
        }
        this.variant = variant;
        this.params = params;
        types = variant.pieces().size();
        squares = variant.grid().squares();
        material = new int[types];
        mobility = new int[types];
        square = new int[types * squares];
        boolean anyMobility = false;
        for (int t = 0; t < types; t++) {
            material[t] = params.get(t * (2 + squares));
            mobility[t] = params.get(t * (2 + squares) + 1);
            anyMobility |= mobility[t] != 0;
            for (int sq = 0; sq < squares; sq++) {
                square[t * squares + sq] = params.get(t * (2 + squares) + 2 + sq);
            }
        }
        usesMobility = anyMobility;
    }

    /** The defaults for {@code variant}. */
    public static PieceSetEvaluate of(Variant variant) {
        return new PieceSetEvaluate(variant, schema(variant).defaults());
    }

    /** The parameters for {@code variant}'s pieces: per type material, mobility, then a value per square. */
    public static ParamSchema schema(Variant variant) {
        return SCHEMAS.computeIfAbsent(variant, PieceSetEvaluate::buildSchema);
    }

    private static ParamSchema buildSchema(Variant variant) {
        boolean fromZero = variant.has(ai.variant.WinCondition.Kind.LOSE_EVERYTHING);
        List<ParamSpec> specs = new ArrayList<>();
        for (PieceType type : variant.pieces()) {
            String name = type.name().toLowerCase().replaceAll("[^a-z0-9]+", "-");
            int value = fromZero || type.royal() ? 0 : Math.max(-2000, Math.min(2000, type.value()));
            specs.add(new ParamSpec("material." + name, "material", value, -2000, 2000,
                    "What one " + type.name() + " is worth"));
            specs.add(new ParamSpec("mobility." + name, "mobility", 0, -50, 50,
                    "What each square a " + type.name() + " can go to is worth"));
            for (int sq = 0; sq < variant.grid().squares(); sq++) {
                specs.add(new ParamSpec("square." + name + "." + squareName(variant, sq), "square", 0, -300, 300,
                        "What a " + type.name() + " is worth on this square, seen from its owner's side"));
            }
        }
        return new ParamSchema(specs);
    }

    private static String squareName(Variant variant, int sq) {
        int width = variant.grid().width();
        return "" + (char) ('a' + sq % width) + (variant.grid().height() - sq / width);
    }

    @Override
    public ParamVector params() {
        return params;
    }

    public Variant variant() {
        return variant;
    }

    @Override
    public int evaluate(Board position, int rootPlayer) {
        Outcome outcome = position.outcome();
        if (outcome != Outcome.ONGOING) {
            int forMover = outcome == Outcome.WIN ? MATE : outcome == Outcome.LOSS ? -MATE : 0;
            return position.sideToMove() == rootPlayer ? forMover : -forMover;
        }
        PieceBoard board = (PieceBoard) position;
        int score = side(board, rootPlayer) - side(board, 1 - rootPlayer);
        return score;
    }

    private int side(PieceBoard board, int player) {
        int score = 0;
        for (int t = 0; t < types; t++) {
            long pieces = board.pieces(player, t);
            if (pieces == 0) {
                continue;
            }
            score += material[t] * Long.bitCount(pieces);
            for (long b = pieces; b != 0; b &= b - 1) {
                int sq = Long.numberOfTrailingZeros(b);
                score += square[t * squares + (player == 0 ? sq : squares - 1 - sq)];
            }
            if (usesMobility && mobility[t] != 0) {
                score += mobility[t] * board.mobility(player, t);
            }
        }
        return score;
    }
}
