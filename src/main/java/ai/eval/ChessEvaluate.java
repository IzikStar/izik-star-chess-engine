package ai.eval;

import ai.board.Board;
import ai.board.ChessPosition;
import ai.eval.Evaluator;
import ai.eval.ParamSchema;
import ai.eval.ParamSpec;
import ai.eval.ParamVector;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hand-written evaluation, with every number a named parameter (Phase 5,
 * docs/phase-5-research.md §5.1 and §8.2).
 *
 * <p>The score is a sum of <em>features</em> times <em>weights</em>. A feature is a count measured
 * on the board, always Black's count minus White's (e.g. "knights": Black's knights minus White's).
 * Each feature has two weights, one for the middlegame and one for the endgame, blended by how
 * much material is left ("tapered"): {@code (mg * phase + eg * (24 - phase)) / 24}, phase 24 with
 * all pieces on the board and 0 with only kings and pawns. Every weight is a bonus for the side
 * that has the feature; a penalty is a negative weight. On top of the named features there are
 * piece-square tables: a bonus for each piece type on each square (left-right mirrored, so 32
 * squares per piece type).
 *
 * <p>Scores are in centipawns: a pawn is 100. (Until the evolution groundwork's second round they
 * were tenths of a pawn; old parameter files are converted when read, see {@link ParamVector}.)
 *
 * <p>{@link #CLASSIC} is the numbers the engine always used, times 10, with the same value for
 * middlegame and endgame (so the blend gives back exactly that value), and 0 for every feature
 * added in Phase 5. It plays exactly the old moves ({@code engine.SameMoveTest}). The schema
 * defaults ({@link #DEFAULT}) are the Texel-tuned weights, where evolution starts. Features whose weights are
 * all 0 are not computed, so the extra parameters cost nothing until they are used.
 *
 * <p>An instance holds only its weights; nothing is written while evaluating, so one instance can
 * serve searches on several threads.
 */
public final class ChessEvaluate implements Evaluator {

    /**
     * Score of a mated side, from the winner's point of view. Kept well inside {@code int} range
     * so it can be negated: the old code returned {@code -Integer.MIN_VALUE}, which overflows back
     * to {@code MIN_VALUE}, so an engine playing White scored "I deliver mate" as its worst outcome.
     */
    public static final int MATE = Evaluator.MATE;

    /** Phase of a full board: knights and bishops count 1, rooks 2, queens 4. */
    public static final int MAX_PHASE = 24;

    // ---- the named features, in schema order -------------------------------------------------

    private static final List<Feature> FEATURES = new ArrayList<>();

    private static Feature feature(String name, String group, int defaultValue, int min, int max, String description) {
        Feature f = new Feature(FEATURES.size(), name, group, defaultValue, min, max, description);
        FEATURES.add(f);
        return f;
    }

    private record Feature(int index, String name, String group, int defaultValue, int min, int max, String description) {}

    // material
    private static final Feature PAWNS = feature("material.pawn", "material", 100, 0, 3000, "Value of a pawn");
    private static final Feature KNIGHTS = feature("material.knight", "material", 300, 0, 9000, "Value of a knight");
    private static final Feature BISHOPS = feature("material.bishop", "material", 330, 0, 9000, "Value of a bishop");
    private static final Feature ROOKS = feature("material.rook", "material", 500, 0, 15000, "Value of a rook");
    private static final Feature QUEENS = feature("material.queen", "material", 900, 0, 27000, "Value of a queen");
    // pawn advance
    private static final Feature PAWN_RANK7 = feature("pawns.rank7", "pawns", 90, -1000, 3000, "Pawn one step from promoting");
    private static final Feature PAWN_RANK6 = feature("pawns.rank6", "pawns", 70, -1000, 3000, "Pawn two steps from promoting");
    private static final Feature PAWN_RANK5 = feature("pawns.rank5", "pawns", 50, -1000, 3000, "Pawn three steps from promoting");
    private static final Feature PAWN_RANK4 = feature("pawns.rank4", "pawns", 30, -1000, 3000, "Pawn four steps from promoting");
    private static final Feature PAWN_RANK3 = feature("pawns.rank3", "pawns", 10, -1000, 3000, "Pawn that has made one step");
    private static final Feature CENTER_PAWNS = feature("pawns.center", "pawns", 50, -1000, 3000, "Pawn on d4, e4, d5 or e5");
    // pawn structure (Phase 5)
    private static final Feature DOUBLED = feature("pawns.doubled", "pawnStructure", 0, -3000, 3000, "Each extra pawn on a file");
    private static final Feature ISOLATED = feature("pawns.isolated", "pawnStructure", 0, -3000, 3000, "Pawn with no own pawn on the files beside it");
    private static final Feature DEFENDED = feature("pawns.defended", "pawnStructure", 0, -3000, 3000, "Pawn defended by another pawn");
    private static final Feature PASSED_R2 = feature("pawns.passed.rank2", "pawnStructure", 0, -3000, 6000, "Passed pawn on its second rank");
    private static final Feature PASSED_R3 = feature("pawns.passed.rank3", "pawnStructure", 0, -3000, 6000, "Passed pawn on its third rank");
    private static final Feature PASSED_R4 = feature("pawns.passed.rank4", "pawnStructure", 0, -3000, 6000, "Passed pawn on its fourth rank");
    private static final Feature PASSED_R5 = feature("pawns.passed.rank5", "pawnStructure", 0, -3000, 6000, "Passed pawn on its fifth rank");
    private static final Feature PASSED_R6 = feature("pawns.passed.rank6", "pawnStructure", 0, -3000, 6000, "Passed pawn on its sixth rank");
    private static final Feature PASSED_R7 = feature("pawns.passed.rank7", "pawnStructure", 0, -3000, 6000, "Passed pawn on its seventh rank");
    private static final Feature PASSED_PROTECTED = feature("pawns.passed.protected", "pawnStructure", 0, -3000, 3000, "Passed pawn defended by a pawn");
    private static final Feature PASSED_BLOCKED = feature("pawns.passed.blocked", "pawnStructure", 0, -3000, 3000, "Passed pawn with a piece right in front of it");
    // king placement (gated by king.safetyUntilTurn)
    private static final Feature KING_CASTLED_SQUARE = feature("king.castledSquare", "king", 100, -3000, 3000, "King on g1/b1 (g8/b8), early in the game");
    private static final Feature KING_NEAR_CASTLED = feature("king.nearCastledSquare", "king", 50, -3000, 3000, "King on f1/c1 (f8/c8), early in the game");
    private static final Feature KING_BACK_RANK = feature("king.backRank", "king", 0, -3000, 3000, "King elsewhere on its back rank, early in the game");
    private static final Feature KING_SECOND_RANK = feature("king.secondRank", "king", -70, -3000, 3000, "King on its second rank, early in the game");
    private static final Feature KING_EXPOSED = feature("king.exposed", "king", -250, -3000, 3000, "King further up the board, early in the game");
    // king safety (Phase 5)
    private static final Feature SHIELD_NEAR = feature("kingSafety.shieldNear", "kingSafety", 0, -1000, 1000, "Own pawn on the three squares in front of the king");
    private static final Feature SHIELD_FAR = feature("kingSafety.shieldFar", "kingSafety", 0, -1000, 1000, "Own pawn two squares in front of the king (three files)");
    private static final Feature KING_OPEN_FILE = feature("kingSafety.openFile", "kingSafety", 0, -1000, 1000, "File at or beside the king with no pawns");
    private static final Feature KING_HALF_OPEN_FILE = feature("kingSafety.halfOpenFile", "kingSafety", 0, -1000, 1000, "File at or beside the king with only enemy pawns");
    private static final Feature KNIGHT_ATTACKER = feature("kingSafety.knightAttacker", "kingSafety", 0, -1000, 1000, "Enemy knight attacking the squares around the king");
    private static final Feature BISHOP_ATTACKER = feature("kingSafety.bishopAttacker", "kingSafety", 0, -1000, 1000, "Enemy bishop attacking the squares around the king");
    private static final Feature ROOK_ATTACKER = feature("kingSafety.rookAttacker", "kingSafety", 0, -1000, 1000, "Enemy rook attacking the squares around the king");
    private static final Feature QUEEN_ATTACKER = feature("kingSafety.queenAttacker", "kingSafety", 0, -1000, 1000, "Enemy queen attacking the squares around the king");
    // castling
    private static final Feature LOST_KING_SIDE = feature("castling.lostKingSide", "castling", -60, -3000, 3000, "Having lost the right to castle king-side");
    private static final Feature LOST_QUEEN_SIDE = feature("castling.lostQueenSide", "castling", -40, -3000, 3000, "Having lost the right to castle queen-side");
    private static final Feature CASTLED = feature("castling.castled", "castling", 160, -3000, 3000, "Having castled");
    // activity
    private static final Feature ATTACKED_SQUARES = feature("activity.attackedSquare", "activity", 10, -300, 300, "Each square a side attacks");
    private static final Feature ATTACKED_ENEMIES = feature("activity.attackedEnemyPiece", "activity", 20, -300, 300, "Each enemy piece attacked");
    private static final Feature DEFENDED_PIECES = feature("activity.defendedOwnPiece", "activity", 10, -300, 300, "Each own piece defended");
    // mobility (Phase 5)
    private static final Feature KNIGHT_MOBILITY = feature("mobility.knight", "mobility", 0, -300, 300, "Each square a knight can move to");
    private static final Feature BISHOP_MOBILITY = feature("mobility.bishop", "mobility", 0, -300, 300, "Each square a bishop can move to");
    private static final Feature ROOK_MOBILITY = feature("mobility.rook", "mobility", 0, -300, 300, "Each square a rook can move to");
    private static final Feature QUEEN_MOBILITY = feature("mobility.queen", "mobility", 0, -300, 300, "Each square a queen can move to");
    // development
    private static final Feature BISHOPS_HOME = feature("development.bishopsHome", "development", -150, -3000, 3000, "A bishop still on the back rank");
    private static final Feature QUEEN_OUT_EARLY = feature("development.queenOutEarly", "development", -150, -3000, 3000, "Queen off its square in the opening");
    private static final Feature KNIGHTS_HOME = feature("development.knightsHome", "development", -100, -3000, 3000, "A knight still on the back rank");
    private static final Feature KNIGHT_GOOD_SQUARE = feature("development.knightOnC3F3", "development", 20, -3000, 3000, "Each knight on c3/f3 (c6/f6)");
    private static final Feature KNIGHT_OUT_EARLY = feature("development.knightOutEarly", "development", -50, -3000, 3000, "A knight far up the board early on");
    // pieces (Phase 5)
    private static final Feature BISHOP_PAIR = feature("pieces.bishopPair", "pieces", 0, -3000, 3000, "Having both bishops");
    private static final Feature ROOK_OPEN_FILE = feature("pieces.rookOpenFile", "pieces", 0, -3000, 3000, "Rook on a file with no pawns");
    private static final Feature ROOK_HALF_OPEN_FILE = feature("pieces.rookHalfOpenFile", "pieces", 0, -3000, 3000, "Rook on a file with only enemy pawns");
    private static final Feature ROOK_ON_SEVENTH = feature("pieces.rookOnSeventh", "pieces", 0, -3000, 3000, "Rook on its seventh rank");
    private static final Feature KNIGHT_OUTPOST = feature("pieces.knightOutpost", "pieces", 0, -3000, 3000, "Knight in the enemy half, defended by a pawn, no enemy pawn can chase it");
    private static final Feature TEMPO = feature("pieces.tempo", "pieces", 0, -1000, 1000, "Being the side to move");

    /** Settings that switch features on and off; one value, not tapered. */
    private record Gate(String name, String group, int defaultValue, int min, int max, String description) {}

    private static final Gate[] GATES = {
            new Gate("king.safetyUntilTurn", "king", 20, 0, 300, "The king placement features count before this turn"),
            new Gate("development.openingUntilTurn", "development", 8, 0, 300, "The game counts as the opening (queen out early) before this turn"),
            new Gate("development.knightEarlyUntilTurn", "development", 9, 0, 300, "The knight-out-early feature counts before this turn"),
    };

    private static final String[] PIECE_NAMES = {"pawn", "knight", "bishop", "rook", "queen", "king"};

    /** Number of named features. */
    public static final int NAMED = FEATURES.size();
    /** Piece-square entries per phase: 6 piece types × 32 squares (files a-d, mirrored). */
    public static final int PST_SIZE = 6 * 32;

    /** Every parameter: each named feature's mg and eg weight, the gates, then the piece-square tables (mg, eg). */
    public static final ParamSchema SCHEMA;

    static {
        List<ParamSpec> specs = new ArrayList<>();
        for (Feature f : FEATURES) {
            specs.add(new ParamSpec(f.name() + ".mg", f.group(), f.defaultValue(), f.min(), f.max(), f.description() + " (middlegame)"));
            specs.add(new ParamSpec(f.name() + ".eg", f.group(), f.defaultValue(), f.min(), f.max(), f.description() + " (endgame)"));
        }
        for (Gate g : GATES) {
            specs.add(new ParamSpec(g.name(), g.group(), g.defaultValue(), g.min(), g.max(), g.description()));
        }
        for (String phase : new String[]{"mg", "eg"}) {
            for (int piece = 0; piece < 6; piece++) {
                for (int i = 0; i < 32; i++) {
                    specs.add(new ParamSpec("pst." + PIECE_NAMES[piece] + "." + pstSquareName(i) + "." + phase, "pst." + PIECE_NAMES[piece],
                            0, -2000, 2000, "A " + PIECE_NAMES[piece] + " on " + pstSquareName(i) + " or its mirror (" + phase + ")"));
                }
            }
        }
        Set<String> gates = new HashSet<>();
        for (Gate g : GATES) {
            gates.add(g.name());
        }
        // The defaults are the Texel-tuned weights (presets/tuned-v1.json), where evolution starts.
        ParamSchema untuned = new ParamSchema(specs, gates);
        int[] tuned = preset(untuned, "tuned-v1").toArray();
        List<ParamSpec> tunedSpecs = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            ParamSpec s = specs.get(i);
            tunedSpecs.add(new ParamSpec(s.name(), s.group(), tuned[i], s.min(), s.max(), s.description()));
        }
        SCHEMA = new ParamSchema(tunedSpecs, gates);
    }

    /**
     * The evaluation with every parameter at its schema default: the Texel-tuned weights
     * ({@code presets/tuned-v1.json}, fitted by {@code lab.Cli tune} to Stockfish self-play), where
     * evolution starts.
     */
    public static final ChessEvaluate DEFAULT = new ChessEvaluate(SCHEMA.defaults());

    /**
     * The hand-written weights the engine has always played with ({@code presets/classic.json}),
     * kept as they are so they can be compared against and improved by hand. The game plays with
     * these until other weights beat them in the arena.
     */
    public static final ChessEvaluate CLASSIC = new ChessEvaluate(preset("classic"));

    /** A saved set of weights from {@code src/main/resources/presets/<name>.json}. */
    public static ParamVector preset(String name) {
        return preset(SCHEMA, name);
    }

    private static ParamVector preset(ParamSchema schema, String name) {
        try (InputStream in = ChessEvaluate.class.getResourceAsStream("/presets/" + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("no preset " + name);
            }
            return ParamVector.fromJson(schema, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Table index 0-31 → square name from White's side, files a-d (e-h mirror them). */
    private static String pstSquareName(int i) {
        int row = i / 4;      // 0 = eighth rank
        int file = i % 4;
        return "" + (char) ('a' + file) + (8 - row);
    }

    // ---- board geometry ----------------------------------------------------------------------
    // squares: a8 = 0 … h1 = 63 (row 0 is the eighth rank); White moves towards row 0

    private static final long[] FILES = new long[8];
    /** {@code FRONT_SPAN[color][sq]}: squares ahead of sq on its file and the files beside it. */
    private static final long[][] FRONT_SPAN = new long[2][64];
    /** {@code ADJACENT_FRONT[color][sq]}: squares ahead of sq on the files beside it only. */
    private static final long[][] ADJACENT_FRONT = new long[2][64];

    static {
        for (int f = 0; f < 8; f++) {
            FILES[f] = BoardParts.A_FILE << f;
        }
        for (int sq = 0; sq < 64; sq++) {
            int row = sq >>> 3;
            int col = sq & 7;
            for (int r = 0; r < 8; r++) {
                for (int c = Math.max(0, col - 1); c <= Math.min(7, col + 1); c++) {
                    long b = 1L << (r * 8 + c);
                    if (r < row) { // ahead for White
                        FRONT_SPAN[1][sq] |= b;
                        if (c != col) ADJACENT_FRONT[1][sq] |= b;
                    }
                    if (r > row) { // ahead for Black
                        FRONT_SPAN[0][sq] |= b;
                        if (c != col) ADJACENT_FRONT[0][sq] |= b;
                    }
                }
            }
        }
    }

    private static final long[] WHITE_KING_SQUARES = {
            (BoardParts.Tile.G1.position | BoardParts.Tile.B1.position),
            (BoardParts.Tile.F1.position | BoardParts.Tile.C1.position),
            (BoardParts.FIRST_RANK),
            (BoardParts.SECOND_RANK)
    };

    private static final long[] BLACK_KING_SQUARES = {
            (BoardParts.Tile.G8.position | BoardParts.Tile.B8.position),
            (BoardParts.Tile.F8.position | BoardParts.Tile.C8.position),
            (BoardParts.EIGHTH_RANK),
            (BoardParts.SEVENTH_RANK)
    };

    private static final long WHITE_KNIGHT_SQUARES = (BoardParts.Tile.F3.position | BoardParts.Tile.C3.position);
    private static final long BLACK_KNIGHT_SQUARES = (BoardParts.Tile.F6.position | BoardParts.Tile.C6.position);

    /** Ranks a pawn advances through, nearest to promotion first. */
    private static final long[] WHITE_PAWN_RANKS = {
            BoardParts.SEVENTH_RANK, BoardParts.SIXTH_RANK, BoardParts.FIFTH_RANK, BoardParts.FOURTH_RANK, BoardParts.THIRD_RANK
    };

    private static final long[] BLACK_PAWN_RANKS = {
            BoardParts.SECOND_RANK, BoardParts.THIRD_RANK, BoardParts.FOURTH_RANK, BoardParts.FIFTH_RANK, BoardParts.SIXTH_RANK
    };

    // ---- an instance: the weights ------------------------------------------------------------

    private final ParamVector params;
    private final int[] mg = new int[NAMED];
    private final int[] eg = new int[NAMED];
    private final int kingSafetyUntilTurn, openingUntilTurn, knightEarlyUntilTurn;
    /** {@code pstMg[piece][i]}, i = table index 0-31. */
    private final int[][] pstMg = new int[6][32];
    private final int[][] pstEg = new int[6][32];
    private final boolean usesPst;
    /** Which feature groups have a non-zero weight (so the others are skipped). */
    private final boolean pawnStructure, kingSafety, mobility, pieces, activity;

    public ChessEvaluate(ParamVector params) {
        if (params.schema() != SCHEMA) {
            throw new IllegalArgumentException("parameters are not for the hand-written evaluation");
        }
        this.params = params;
        int k = 0;
        for (int i = 0; i < NAMED; i++) {
            mg[i] = params.get(k++);
            eg[i] = params.get(k++);
        }
        kingSafetyUntilTurn = params.get(k++);
        openingUntilTurn = params.get(k++);
        knightEarlyUntilTurn = params.get(k++);
        boolean any = false;
        for (int[][] table : new int[][][]{pstMg, pstEg}) {
            for (int piece = 0; piece < 6; piece++) {
                for (int i = 0; i < 32; i++) {
                    table[piece][i] = params.get(k++);
                    any |= table[piece][i] != 0;
                }
            }
        }
        usesPst = any;
        pawnStructure = uses("pawnStructure");
        kingSafety = uses("kingSafety");
        mobility = uses("mobility");
        pieces = uses("pieces");
        activity = uses("activity");
    }

    private boolean uses(String group) {
        for (Feature f : FEATURES) {
            if (f.group().equals(group) && (mg[f.index()] != 0 || eg[f.index()] != 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ParamVector params() {
        return params;
    }

    /**
     * Static evaluation of {@code board} from the point of view of the side choosing the move at
     * the search root ({@code rootPlayer}: 0 White, 1 Black).
     */
    @Override
    public int evaluate(Board position, int rootPlayer) {
        ChessPosition chess = (ChessPosition) position;
        Bits board = new Bits(chess);
        boolean switchSides = rootPlayer == 1;
        int value;
        if (board.whiteKings == 0) return switchSides ? MATE : -MATE;
        if (board.blackKings == 0) return switchSides ? -MATE : MATE;
        value = status(chess);
        if (value != 1) {
            // getStatus(): MIN_VALUE = Black is mated, MAX_VALUE = White is mated, 0 = draw.
            value = value == Integer.MIN_VALUE ? -MATE : value == Integer.MAX_VALUE ? MATE : 0;
            return switchSides ? value : -value;
        }
        int[] f = new int[NAMED];
        measure(board, f, false);
        int phase = phase(board);
        long mgSum = 0;
        long egSum = 0;
        for (int i = 0; i < NAMED; i++) {
            if (f[i] != 0) {
                mgSum += (long) f[i] * mg[i];
                egSum += (long) f[i] * eg[i];
            }
        }
        if (usesPst) {
            mgSum += pst(board, pstMg);
            egSum += pst(board, pstEg);
        }
        value = (int) ((mgSum * phase + egSum * (MAX_PHASE - phase)) / MAX_PHASE);
        return switchSides ? value : -value;
    }

    /**
     * Every feature of {@code board}, for fitting weights to game results (docs/phase-5-research.md
     * §8.3): the named features in schema order (Black minus White, gated features 0 when their
     * gate is closed), then for each piece type and table square Black's pieces minus White's.
     * The score is then {@code taper(Σ feature × mg weight, Σ feature × eg weight, phase)}.
     */
    public Features features(Board position) {
        Bits board = new Bits((ChessPosition) position);
        int[] f = new int[NAMED + PST_SIZE];
        measure(board, f, true);
        for (int piece = 0; piece < 6; piece++) {
            long white = pieces(board, piece, true);
            long black = pieces(board, piece, false);
            for (long p = white; p != 0; p &= p - 1) {
                f[NAMED + piece * 32 + pstIndex(Long.numberOfTrailingZeros(p), true)]--;
            }
            for (long p = black; p != 0; p &= p - 1) {
                f[NAMED + piece * 32 + pstIndex(Long.numberOfTrailingZeros(p), false)]++;
            }
        }
        return new Features(f, phase(board), board.isWhiteToMove);
    }

    /**
     * A position's features (see {@link #features}) and game phase (0-24).
     *
     * @param whiteToMove whose move it is (the features are always Black minus White)
     */
    public record Features(int[] values, int phase, boolean whiteToMove) {}

    /**
     * 1 while the game goes on; when it is over, Integer.MIN_VALUE when Black is mated,
     * Integer.MAX_VALUE when White is mated and 0 for a draw (stalemate or the 50-move rule).
     */
    private static int status(ChessPosition p) {
        if (!p.hasLegalMove()) {
            if (p.inCheck(1)) return Integer.MIN_VALUE;
            if (p.inCheck(0)) return Integer.MAX_VALUE;
            return 0;
        }
        return p.halfmoveClock() >= 100 ? 0 : 1;
    }

    /**
     * The position's piece sets under the names the measuring code reads (Phase 6: the evaluation
     * reads any {@link ChessPosition}, not the bitboard's fields).
     */
    private static final class Bits {
        final ChessPosition position;
        final long whiteKings, whiteQueens, whiteRooks, whiteBishops, whiteKnights, whitePawns;
        final long blackKings, blackQueens, blackRooks, blackBishops, blackKnights, blackPawns;
        final long whitePieces, blackPieces;
        final int numOfTurns;
        final boolean isWhiteToMove, hasWhiteCastled, hasBlackCastled;
        final boolean canWhiteCastleKingSide, canWhiteCastleQueenSide, canBlackCastleKingSide, canBlackCastleQueenSide;

        Bits(ChessPosition p) {
            position = p;
            whiteKings = p.pieces(0, ChessPosition.KING);
            whiteQueens = p.pieces(0, ChessPosition.QUEEN);
            whiteRooks = p.pieces(0, ChessPosition.ROOK);
            whiteBishops = p.pieces(0, ChessPosition.BISHOP);
            whiteKnights = p.pieces(0, ChessPosition.KNIGHT);
            whitePawns = p.pieces(0, ChessPosition.PAWN);
            blackKings = p.pieces(1, ChessPosition.KING);
            blackQueens = p.pieces(1, ChessPosition.QUEEN);
            blackRooks = p.pieces(1, ChessPosition.ROOK);
            blackBishops = p.pieces(1, ChessPosition.BISHOP);
            blackKnights = p.pieces(1, ChessPosition.KNIGHT);
            blackPawns = p.pieces(1, ChessPosition.PAWN);
            whitePieces = p.occupied(0);
            blackPieces = p.occupied(1);
            numOfTurns = p.fullmoveNumber();
            isWhiteToMove = p.sideToMove() == 0;
            hasWhiteCastled = p.hasCastled(0);
            hasBlackCastled = p.hasCastled(1);
            canWhiteCastleKingSide = p.canCastle(0, true);
            canWhiteCastleQueenSide = p.canCastle(0, false);
            canBlackCastleKingSide = p.canCastle(1, true);
            canBlackCastleQueenSide = p.canCastle(1, false);
        }

        /** Engine colours: 1 White, 0 Black. */
        long getAllAttackedTiles(int color) {
            return position.attackedBy(color == 1 ? 0 : 1);
        }
    }

    /** 24 with every knight, bishop, rook and queen on the board, 0 with none. */
    static int phase(Bits b) {
        int phase = Long.bitCount(b.whiteKnights | b.blackKnights) + Long.bitCount(b.whiteBishops | b.blackBishops)
                + 2 * Long.bitCount(b.whiteRooks | b.blackRooks) + 4 * Long.bitCount(b.whiteQueens | b.blackQueens);
        return Math.min(MAX_PHASE, phase);
    }

    // ---- measuring ---------------------------------------------------------------------------

    /** Fills {@code f} with Black-minus-White feature counts; groups with no weight are skipped unless {@code all}. */
    private void measure(Bits b, int[] f, boolean all) {
        // material
        f[PAWNS.index()] = Long.bitCount(b.blackPawns) - Long.bitCount(b.whitePawns);
        f[KNIGHTS.index()] = Long.bitCount(b.blackKnights) - Long.bitCount(b.whiteKnights);
        f[BISHOPS.index()] = Long.bitCount(b.blackBishops) - Long.bitCount(b.whiteBishops);
        f[ROOKS.index()] = Long.bitCount(b.blackRooks) - Long.bitCount(b.whiteRooks);
        f[QUEENS.index()] = Long.bitCount(b.blackQueens) - Long.bitCount(b.whiteQueens);

        // pawn advance
        Feature[] advance = {PAWN_RANK7, PAWN_RANK6, PAWN_RANK5, PAWN_RANK4, PAWN_RANK3};
        for (int i = 0; i < advance.length; i++) {
            f[advance[i].index()] = Long.bitCount(b.blackPawns & BLACK_PAWN_RANKS[i]) - Long.bitCount(b.whitePawns & WHITE_PAWN_RANKS[i]);
        }
        f[CENTER_PAWNS.index()] = Long.bitCount(b.blackPawns & BoardParts.CENTER) - Long.bitCount(b.whitePawns & BoardParts.CENTER);

        // king placement, early in the game
        if (b.numOfTurns < kingSafetyUntilTurn) {
            Feature[] places = {KING_CASTLED_SQUARE, KING_NEAR_CASTLED, KING_BACK_RANK, KING_SECOND_RANK, KING_EXPOSED};
            f[places[kingPlace(b.whiteKings, WHITE_KING_SQUARES)].index()]--;
            f[places[kingPlace(b.blackKings, BLACK_KING_SQUARES)].index()]++;
        }

        // castling
        f[LOST_KING_SIDE.index()] = (b.canBlackCastleKingSide ? 0 : 1) - (b.canWhiteCastleKingSide ? 0 : 1);
        f[LOST_QUEEN_SIDE.index()] = (b.canBlackCastleQueenSide ? 0 : 1) - (b.canWhiteCastleQueenSide ? 0 : 1);
        f[CASTLED.index()] = (b.hasBlackCastled ? 1 : 0) - (b.hasWhiteCastled ? 1 : 0);

        // activity
        if (activity || all) {
            long whiteAttacks = b.getAllAttackedTiles(1);
            long blackAttacks = b.getAllAttackedTiles(0);
            f[ATTACKED_SQUARES.index()] = Long.bitCount(blackAttacks) - Long.bitCount(whiteAttacks);
            f[ATTACKED_ENEMIES.index()] = Long.bitCount(blackAttacks & b.whitePieces) - Long.bitCount(whiteAttacks & b.blackPieces);
            f[DEFENDED_PIECES.index()] = Long.bitCount(blackAttacks & b.blackPieces) - Long.bitCount(whiteAttacks & b.whitePieces);
        }

        // development
        f[BISHOPS_HOME.index()] = ((b.blackBishops & BoardParts.EIGHTH_RANK) != 0 ? 1 : 0) - ((b.whiteBishops & BoardParts.FIRST_RANK) != 0 ? 1 : 0);
        if (b.numOfTurns < openingUntilTurn) {
            f[QUEEN_OUT_EARLY.index()] = ((b.blackQueens & BoardParts.Tile.D8.position) == 0 ? 1 : 0)
                    - ((b.whiteQueens & BoardParts.Tile.D1.position) == 0 ? 1 : 0);
        }
        f[KNIGHTS_HOME.index()] = ((b.blackKnights & BoardParts.EIGHTH_RANK) != 0 ? 1 : 0) - ((b.whiteKnights & BoardParts.FIRST_RANK) != 0 ? 1 : 0);
        f[KNIGHT_GOOD_SQUARE.index()] = Long.bitCount(b.blackKnights & BLACK_KNIGHT_SQUARES) - Long.bitCount(b.whiteKnights & WHITE_KNIGHT_SQUARES);
        if (b.numOfTurns < knightEarlyUntilTurn) {
            f[KNIGHT_OUT_EARLY.index()] = ((b.blackKnights & BoardParts.FIRST_FIVE_RANKS) != 0 ? 1 : 0)
                    - ((b.whiteKnights & BoardParts.BACK_FIVE_RANKS) != 0 ? 1 : 0);
        }

        if (pawnStructure || all) {
            pawnStructure(b, f);
        }
        if (kingSafety || all) {
            kingSafety(b, f);
        }
        if (mobility || all) {
            mobility(b, f);
        }
        if (pieces || all) {
            pieces(b, f);
        }
    }

    /** Which of the five king-placement groups the king is in (the last one is "anywhere else"). */
    private static int kingPlace(long king, long[] groups) {
        for (int i = 0; i < groups.length; i++) {
            if ((king & groups[i]) != 0) {
                return i;
            }
        }
        return groups.length;
    }

    private static long pawnAttacks(long pawns, int color) {
        long squares = 0;
        for (long p = pawns; p != 0; p &= p - 1) {
            squares |= Attacks.PAWN[color][Long.numberOfTrailingZeros(p)];
        }
        return squares;
    }

    private void pawnStructure(Bits b, int[] f) {
        Feature[] passedByRank = {PASSED_R2, PASSED_R3, PASSED_R4, PASSED_R5, PASSED_R6, PASSED_R7};
        long occupied = b.whitePieces | b.blackPieces;
        for (int color = 0; color <= 1; color++) {
            boolean white = color == 1;
            int sign = white ? -1 : 1;
            long own = white ? b.whitePawns : b.blackPawns;
            long enemy = white ? b.blackPawns : b.whitePawns;
            long defendedSquares = pawnAttacks(own, color);
            for (int file = 0; file < 8; file++) {
                int onFile = Long.bitCount(own & FILES[file]);
                if (onFile > 1) {
                    f[DOUBLED.index()] += sign * (onFile - 1);
                }
                if (onFile > 0) {
                    long beside = (file > 0 ? FILES[file - 1] : 0) | (file < 7 ? FILES[file + 1] : 0);
                    if ((own & beside) == 0) {
                        f[ISOLATED.index()] += sign * onFile;
                    }
                }
            }
            f[DEFENDED.index()] += sign * Long.bitCount(own & defendedSquares);
            for (long p = own; p != 0; p &= p - 1) {
                int sq = Long.numberOfTrailingZeros(p);
                if ((FRONT_SPAN[color][sq] & enemy) != 0) {
                    continue;
                }
                int row = sq >>> 3;
                int rank = white ? 8 - row : row + 1; // the pawn's rank from its own side
                if (rank >= 2 && rank <= 7) {
                    f[passedByRank[rank - 2].index()] += sign;
                }
                if ((defendedSquares & (1L << sq)) != 0) {
                    f[PASSED_PROTECTED.index()] += sign;
                }
                int ahead = white ? sq - 8 : sq + 8;
                if (ahead >= 0 && ahead < 64 && (occupied & (1L << ahead)) != 0) {
                    f[PASSED_BLOCKED.index()] += sign;
                }
            }
        }
    }

    private void kingSafety(Bits b, int[] f) {
        long occupied = b.whitePieces | b.blackPieces;
        for (int color = 0; color <= 1; color++) {
            boolean white = color == 1;
            int sign = white ? -1 : 1;
            long king = white ? b.whiteKings : b.blackKings;
            if (king == 0) {
                continue;
            }
            int sq = Long.numberOfTrailingZeros(king);
            int row = sq >>> 3;
            int col = sq & 7;
            long own = white ? b.whitePawns : b.blackPawns;
            long enemy = white ? b.blackPawns : b.whitePawns;
            int dir = white ? -1 : 1;
            for (int c = Math.max(0, col - 1); c <= Math.min(7, col + 1); c++) {
                int near = row + dir;
                int far = row + 2 * dir;
                if (near >= 0 && near < 8 && (own & (1L << (near * 8 + c))) != 0) {
                    f[SHIELD_NEAR.index()] += sign;
                }
                if (far >= 0 && far < 8 && (own & (1L << (far * 8 + c))) != 0) {
                    f[SHIELD_FAR.index()] += sign;
                }
                if ((own & FILES[c]) == 0) {
                    f[((enemy & FILES[c]) == 0 ? KING_OPEN_FILE : KING_HALF_OPEN_FILE).index()] += sign;
                }
            }
            long zone = Attacks.KING[sq] | king;
            for (long p = white ? b.blackKnights : b.whiteKnights; p != 0; p &= p - 1) {
                if ((Attacks.KNIGHT[Long.numberOfTrailingZeros(p)] & zone) != 0) f[KNIGHT_ATTACKER.index()] += sign;
            }
            for (long p = white ? b.blackBishops : b.whiteBishops; p != 0; p &= p - 1) {
                if ((Attacks.bishop(Long.numberOfTrailingZeros(p), occupied) & zone) != 0) f[BISHOP_ATTACKER.index()] += sign;
            }
            for (long p = white ? b.blackRooks : b.whiteRooks; p != 0; p &= p - 1) {
                if ((Attacks.rook(Long.numberOfTrailingZeros(p), occupied) & zone) != 0) f[ROOK_ATTACKER.index()] += sign;
            }
            for (long p = white ? b.blackQueens : b.whiteQueens; p != 0; p &= p - 1) {
                int q = Long.numberOfTrailingZeros(p);
                if (((Attacks.rook(q, occupied) | Attacks.bishop(q, occupied)) & zone) != 0) f[QUEEN_ATTACKER.index()] += sign;
            }
        }
        // an attacker near the enemy king is a bonus for the attacker: the counts above are per
        // defending side, so flip them (Black's attackers near White's king count for Black)
        for (Feature a : new Feature[]{KNIGHT_ATTACKER, BISHOP_ATTACKER, ROOK_ATTACKER, QUEEN_ATTACKER}) {
            f[a.index()] = -f[a.index()];
        }
    }

    private void mobility(Bits b, int[] f) {
        long occupied = b.whitePieces | b.blackPieces;
        for (int color = 0; color <= 1; color++) {
            boolean white = color == 1;
            int sign = white ? -1 : 1;
            long free = ~(white ? b.whitePieces : b.blackPieces);
            for (long p = white ? b.whiteKnights : b.blackKnights; p != 0; p &= p - 1) {
                f[KNIGHT_MOBILITY.index()] += sign * Long.bitCount(Attacks.KNIGHT[Long.numberOfTrailingZeros(p)] & free);
            }
            for (long p = white ? b.whiteBishops : b.blackBishops; p != 0; p &= p - 1) {
                f[BISHOP_MOBILITY.index()] += sign * Long.bitCount(Attacks.bishop(Long.numberOfTrailingZeros(p), occupied) & free);
            }
            for (long p = white ? b.whiteRooks : b.blackRooks; p != 0; p &= p - 1) {
                f[ROOK_MOBILITY.index()] += sign * Long.bitCount(Attacks.rook(Long.numberOfTrailingZeros(p), occupied) & free);
            }
            for (long p = white ? b.whiteQueens : b.blackQueens; p != 0; p &= p - 1) {
                int q = Long.numberOfTrailingZeros(p);
                f[QUEEN_MOBILITY.index()] += sign * Long.bitCount((Attacks.rook(q, occupied) | Attacks.bishop(q, occupied)) & free);
            }
        }
    }

    private void pieces(Bits b, int[] f) {
        long allPawns = b.whitePawns | b.blackPawns;
        for (int color = 0; color <= 1; color++) {
            boolean white = color == 1;
            int sign = white ? -1 : 1;
            long ownPawns = white ? b.whitePawns : b.blackPawns;
            long enemyPawns = white ? b.blackPawns : b.whitePawns;
            if (Long.bitCount(white ? b.whiteBishops : b.blackBishops) >= 2) {
                f[BISHOP_PAIR.index()] += sign;
            }
            long seventh = white ? BoardParts.SEVENTH_RANK : BoardParts.SECOND_RANK;
            for (long p = white ? b.whiteRooks : b.blackRooks; p != 0; p &= p - 1) {
                int sq = Long.numberOfTrailingZeros(p);
                long file = FILES[sq & 7];
                if ((allPawns & file) == 0) {
                    f[ROOK_OPEN_FILE.index()] += sign;
                } else if ((ownPawns & file) == 0) {
                    f[ROOK_HALF_OPEN_FILE.index()] += sign;
                }
                if ((seventh & (1L << sq)) != 0) {
                    f[ROOK_ON_SEVENTH.index()] += sign;
                }
            }
            long defended = pawnAttacks(ownPawns, color);
            for (long p = white ? b.whiteKnights : b.blackKnights; p != 0; p &= p - 1) {
                int sq = Long.numberOfTrailingZeros(p);
                int rank = white ? 8 - (sq >>> 3) : (sq >>> 3) + 1;
                if (rank >= 4 && rank <= 6 && (defended & (1L << sq)) != 0 && (ADJACENT_FRONT[color][sq] & enemyPawns) == 0) {
                    f[KNIGHT_OUTPOST.index()] += sign;
                }
            }
        }
        f[TEMPO.index()] = b.isWhiteToMove ? -1 : 1;
    }

    // ---- piece-square tables -----------------------------------------------------------------

    private static long pieces(Bits b, int piece, boolean white) {
        return switch (piece) {
            case 0 -> white ? b.whitePawns : b.blackPawns;
            case 1 -> white ? b.whiteKnights : b.blackKnights;
            case 2 -> white ? b.whiteBishops : b.blackBishops;
            case 3 -> white ? b.whiteRooks : b.blackRooks;
            case 4 -> white ? b.whiteQueens : b.blackQueens;
            default -> white ? b.whiteKings : b.blackKings;
        };
    }

    /** The table index of square {@code sq} seen from {@code white}'s side, files mirrored onto a-d. */
    private static int pstIndex(int sq, boolean white) {
        int row = sq >>> 3;
        int col = sq & 7;
        int relativeRow = white ? row : 7 - row;
        int file = Math.min(col, 7 - col);
        return relativeRow * 4 + file;
    }

    /** Black's table bonuses minus White's. */
    private static long pst(Bits b, int[][] table) {
        long sum = 0;
        for (int piece = 0; piece < 6; piece++) {
            int[] t = table[piece];
            for (long p = pieces(b, piece, false); p != 0; p &= p - 1) {
                sum += t[pstIndex(Long.numberOfTrailingZeros(p), false)];
            }
            for (long p = pieces(b, piece, true); p != 0; p &= p - 1) {
                sum -= t[pstIndex(Long.numberOfTrailingZeros(p), true)];
            }
        }
        return sum;
    }
}
