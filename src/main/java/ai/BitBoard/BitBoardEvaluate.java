package ai.BitBoard;

import ai.eval.Evaluator;
import ai.eval.ParamSchema;
import ai.eval.ParamSpec;
import ai.eval.ParamVector;

import java.util.List;

/**
 * The hand-written evaluation. Since Phase 5 every number in it is a named parameter
 * ({@link #SCHEMA}) whose default is the value the code used to hard-code, so
 * {@link #DEFAULT} plays exactly the moves the engine always played (pinned by
 * {@code engine.SameMoveTest}) and any other {@link ParamVector} plays a variant of it.
 *
 * <p>An instance holds only its weights; nothing is written while evaluating, so one instance
 * can serve searches on several threads (the old version kept the game stage in a static field).
 *
 * <p>Internally the terms are computed Black-positive (White's terms are subtracted), as they
 * always were; {@link #evaluate} turns the sum round for the side choosing the move.
 */
public final class BitBoardEvaluate implements Evaluator {

    /**
     * Score of a mated side, from the winner's point of view. Kept well inside {@code int} range
     * so it can be negated: the old code returned {@code -Integer.MIN_VALUE}, which overflows back
     * to {@code MIN_VALUE}, so an engine playing White scored "I deliver mate" as its worst outcome.
     */
    public static final int MATE = 100_000_000;

    private static final ParamSpec[] SPECS = {
            // material
            new ParamSpec("material.pawn", "material", 10, 0, 100, "Value of a pawn"),
            new ParamSpec("material.knight", "material", 30, 0, 300, "Value of a knight"),
            new ParamSpec("material.bishop", "material", 33, 0, 300, "Value of a bishop"),
            new ParamSpec("material.rook", "material", 50, 0, 500, "Value of a rook"),
            new ParamSpec("material.queen", "material", 90, 0, 900, "Value of a queen"),
            // pawns
            new ParamSpec("pawns.rank7", "pawns", 9, -50, 100, "Bonus for a pawn one step from promoting"),
            new ParamSpec("pawns.rank6", "pawns", 7, -50, 100, "Bonus for a pawn two steps from promoting"),
            new ParamSpec("pawns.rank5", "pawns", 5, -50, 100, "Bonus for a pawn three steps from promoting"),
            new ParamSpec("pawns.rank4", "pawns", 3, -50, 100, "Bonus for a pawn four steps from promoting"),
            new ParamSpec("pawns.rank3", "pawns", 1, -50, 100, "Bonus for a pawn that has made its first step"),
            new ParamSpec("pawns.center", "pawns", 5, -50, 100, "Bonus for a pawn on d4, e4, d5 or e5"),
            // king placement while the king-safety table applies
            new ParamSpec("king.castledSquare", "king", 10, -100, 100, "King on g1/b1 (g8/b8)"),
            new ParamSpec("king.nearCastledSquare", "king", 5, -100, 100, "King on f1/c1 (f8/c8)"),
            new ParamSpec("king.backRank", "king", 0, -100, 100, "King elsewhere on its back rank"),
            new ParamSpec("king.secondRank", "king", -7, -100, 100, "King on its second rank"),
            new ParamSpec("king.exposed", "king", -25, -100, 100, "King further up the board"),
            new ParamSpec("king.safetyUntilTurn", "king", 20, 0, 200, "The king placement terms apply before this turn"),
            // castling
            new ParamSpec("castling.lostKingSide", "castling", 6, -50, 100, "Penalty for losing the right to castle king-side"),
            new ParamSpec("castling.lostQueenSide", "castling", 4, -50, 100, "Penalty for losing the right to castle queen-side"),
            new ParamSpec("castling.castled", "castling", 16, -50, 100, "Bonus for having castled"),
            // activity
            new ParamSpec("activity.attackedSquare", "activity", 1, -10, 20, "Bonus per square a side attacks"),
            new ParamSpec("activity.attackedEnemyPiece", "activity", 2, -10, 20, "Extra bonus per enemy piece attacked"),
            new ParamSpec("activity.defendedOwnPiece", "activity", 1, -10, 20, "Bonus per own piece defended"),
            // development
            new ParamSpec("development.openingUntilTurn", "development", 8, 0, 100, "The game counts as the opening before this turn"),
            new ParamSpec("development.bishopsHome", "development", 15, -50, 100, "Penalty if a bishop is still on the back rank"),
            new ParamSpec("development.queenOutEarly", "development", 15, -50, 100, "Penalty for moving the queen in the opening"),
            new ParamSpec("development.knightsHome", "development", 10, -50, 100, "Penalty if a knight is still on the back rank"),
            new ParamSpec("development.knightOnC3F3", "development", 2, -50, 100, "Bonus per knight on c3/f3 (c6/f6)"),
            new ParamSpec("development.knightOutEarly", "development", 5, -50, 100, "Penalty for a knight far up the board early on"),
            new ParamSpec("development.knightEarlyUntilTurn", "development", 9, 0, 100, "The early-knight penalty applies before this turn"),
    };

    /** Every parameter of the hand-written evaluation, in a fixed order. */
    public static final ParamSchema SCHEMA = new ParamSchema(List.of(SPECS));

    /** The evaluation with the weights the engine has always used. */
    public static final BitBoardEvaluate DEFAULT = new BitBoardEvaluate(SCHEMA.defaults());

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
            BoardParts.SEVENTH_RANK,
            BoardParts.SIXTH_RANK,
            BoardParts.FIFTH_RANK,
            BoardParts.FOURTH_RANK,
            BoardParts.THIRD_RANK
    };

    private static final long[] BLACK_PAWN_RANKS = {
            BoardParts.SECOND_RANK,
            BoardParts.THIRD_RANK,
            BoardParts.FOURTH_RANK,
            BoardParts.FIFTH_RANK,
            BoardParts.SIXTH_RANK
    };

    private final ParamVector params;

    // the weights, read once from the vector so evaluating stays plain field arithmetic
    private final int pawn, knight, bishop, rook, queen;
    private final int[] pawnRanks = new int[5];
    private final int centerPawn;
    private final int[] kingSquares = new int[5];
    private final int kingSafetyUntilTurn;
    private final int lostKingSide, lostQueenSide, castled;
    private final int attackedSquare, attackedEnemyPiece, defendedOwnPiece;
    private final int openingUntilTurn;
    private final int bishopsHome, queenOutEarly, knightsHome, knightOnGoodSquare, knightOutEarly, knightEarlyUntilTurn;

    public BitBoardEvaluate(ParamVector params) {
        if (params.schema() != SCHEMA) {
            throw new IllegalArgumentException("parameters are not for the hand-written evaluation");
        }
        this.params = params;
        pawn = params.get("material.pawn");
        knight = params.get("material.knight");
        bishop = params.get("material.bishop");
        rook = params.get("material.rook");
        queen = params.get("material.queen");
        String[] ranks = {"pawns.rank7", "pawns.rank6", "pawns.rank5", "pawns.rank4", "pawns.rank3"};
        for (int i = 0; i < ranks.length; i++) {
            pawnRanks[i] = params.get(ranks[i]);
        }
        centerPawn = params.get("pawns.center");
        String[] king = {"king.castledSquare", "king.nearCastledSquare", "king.backRank", "king.secondRank", "king.exposed"};
        for (int i = 0; i < king.length; i++) {
            kingSquares[i] = params.get(king[i]);
        }
        kingSafetyUntilTurn = params.get("king.safetyUntilTurn");
        lostKingSide = params.get("castling.lostKingSide");
        lostQueenSide = params.get("castling.lostQueenSide");
        castled = params.get("castling.castled");
        attackedSquare = params.get("activity.attackedSquare");
        attackedEnemyPiece = params.get("activity.attackedEnemyPiece");
        defendedOwnPiece = params.get("activity.defendedOwnPiece");
        openingUntilTurn = params.get("development.openingUntilTurn");
        bishopsHome = params.get("development.bishopsHome");
        queenOutEarly = params.get("development.queenOutEarly");
        knightsHome = params.get("development.knightsHome");
        knightOnGoodSquare = params.get("development.knightOnC3F3");
        knightOutEarly = params.get("development.knightOutEarly");
        knightEarlyUntilTurn = params.get("development.knightEarlyUntilTurn");
    }

    @Override
    public ParamVector params() {
        return params;
    }

    /**
     * Static evaluation of {@code board} from the point of view of the side choosing the move at
     * the search root ({@code rootIsBlack}).
     */
    @Override
    public int evaluate(BitBoard board, boolean rootIsBlack) {
        boolean switchSides = rootIsBlack;
        int value;
        if (board.whiteKings == 0) return switchSides ? MATE : -MATE;
        if (board.blackKings == 0) return switchSides ? -MATE : MATE;
        value = board.getStatus();
        if (value != 1) {
            // getStatus(): MIN_VALUE = Black is mated, MAX_VALUE = White is mated, 0 = draw.
            value = value == Integer.MIN_VALUE ? -MATE : value == Integer.MAX_VALUE ? MATE : 0;
            return switchSides ? value : -value;
        }
        value = 0;
        int gameStage = getGameStage(board);
        value += getPiecesPureValue(board);
        value += getPawnsProgress(board);
        value += getKingSafety(board);
        value += getCastles(board);
        value += getTargets(board);
        value += getBishopsDevelopment(board);
        value += getQueensOutInTheOpening(board, gameStage);
        value += getKnightsDevelopment(board);
        return switchSides ? value : -value;
    }

    /**
     * 0 = opening, 1 = otherwise. Stage 2 is meant to be the endgame but is never reached: it
     * counts {@code whitePieces & blackPieces}, which is always empty (docs/phase-5-research.md §2.2).
     */
    private int getGameStage(BitBoard board) {
        int gameStage = 1;
        if (board.numOfTurns < openingUntilTurn) {
            gameStage = 0;
        }
        if (BitOperations.countSetBits(board.whitePieces & board.blackPieces) >= 7) {
            gameStage = 2;
        }
        return gameStage;
    }

    private int getPiecesPureValue(BitBoard board) {
        int piecesValue = 0;
        piecesValue -= BitOperations.countSetBits(board.whitePawns) * pawn;
        piecesValue -= BitOperations.countSetBits(board.whiteKnights) * knight;
        piecesValue -= BitOperations.countSetBits(board.whiteBishops) * bishop;
        piecesValue -= BitOperations.countSetBits(board.whiteRooks) * rook;
        piecesValue -= BitOperations.countSetBits(board.whiteQueens) * queen;

        piecesValue += BitOperations.countSetBits(board.blackPawns) * pawn;
        piecesValue += BitOperations.countSetBits(board.blackKnights) * knight;
        piecesValue += BitOperations.countSetBits(board.blackBishops) * bishop;
        piecesValue += BitOperations.countSetBits(board.blackRooks) * rook;
        piecesValue += BitOperations.countSetBits(board.blackQueens) * queen;
        return piecesValue;
    }

    private int getKingSafety(BitBoard board) {
        int kingSafety = 0;
        if (board.numOfTurns < kingSafetyUntilTurn) {
            for (int i = 0; i < WHITE_KING_SQUARES.length; i++) {
                if ((board.whiteKings & WHITE_KING_SQUARES[i]) != 0) {
                    kingSafety -= kingSquares[i];
                    break;
                }
                if (i == WHITE_KING_SQUARES.length - 1) kingSafety -= kingSquares[i + 1];
            }
            for (int i = 0; i < BLACK_KING_SQUARES.length; i++) {
                if ((board.blackKings & BLACK_KING_SQUARES[i]) != 0) {
                    kingSafety += kingSquares[i];
                    break;
                }
                if (i == BLACK_KING_SQUARES.length - 1) kingSafety += kingSquares[i + 1];
            }
        }
        return kingSafety;
    }

    private int getCastles(BitBoard board) {
        int castles = 0;
        castles += board.canWhiteCastleKingSide ? 0 : lostKingSide;
        castles += board.canWhiteCastleQueenSide ? 0 : lostQueenSide;
        castles -= board.hasWhiteCastled ? castled : 0;
        castles -= board.canBlackCastleKingSide ? 0 : lostKingSide;
        castles -= board.canBlackCastleQueenSide ? 0 : lostQueenSide;
        castles += board.hasBlackCastled ? castled : 0;
        return castles;
    }

    private int getTargets(BitBoard board) {
        long whiteAttacks = board.getAllAttackedTiles(1);
        long blackAttacks = board.getAllAttackedTiles(0);
        int targets = 0;
        targets -= BitOperations.countSetBits(whiteAttacks) * attackedSquare;
        targets += BitOperations.countSetBits(blackAttacks) * attackedSquare;

        targets -= BitOperations.countSetBits((whiteAttacks & board.blackPieces)) * attackedEnemyPiece;
        targets += BitOperations.countSetBits((blackAttacks & board.whitePieces)) * attackedEnemyPiece;

        targets -= BitOperations.countSetBits((whiteAttacks & board.whitePieces)) * defendedOwnPiece;
        targets += BitOperations.countSetBits((blackAttacks & board.blackPieces)) * defendedOwnPiece;
        return targets;
    }

    private int getPawnsProgress(BitBoard board) {
        int pawnProgress = 0;
        for (int i = 0; i < WHITE_PAWN_RANKS.length; i++) {
            pawnProgress -= BitOperations.countSetBits((board.whitePawns & WHITE_PAWN_RANKS[i])) * pawnRanks[i];
        }
        for (int i = 0; i < BLACK_PAWN_RANKS.length; i++) {
            pawnProgress += BitOperations.countSetBits((board.blackPawns & BLACK_PAWN_RANKS[i])) * pawnRanks[i];
        }
        pawnProgress -= BitOperations.countSetBits(board.whitePawns & BoardParts.CENTER) * centerPawn;
        pawnProgress += BitOperations.countSetBits(board.blackPawns & BoardParts.CENTER) * centerPawn;
        return pawnProgress;
    }

    private int getQueensOutInTheOpening(BitBoard board, int gameStage) {
        int queensOutInTheOpening = 0;
        if (gameStage == 0) {
            queensOutInTheOpening += (board.whiteQueens & BoardParts.Tile.D1.position) == 0 ? queenOutEarly : 0;
            queensOutInTheOpening -= (board.blackQueens & BoardParts.Tile.D8.position) == 0 ? queenOutEarly : 0;
        }
        return queensOutInTheOpening;
    }

    private int getKnightsDevelopment(BitBoard board) {
        int knightsDevelopment = 0;
        knightsDevelopment += (board.whiteKnights & BoardParts.FIRST_RANK) != 0 ? knightsHome : 0;
        knightsDevelopment -= (board.blackKnights & BoardParts.EIGHTH_RANK) != 0 ? knightsHome : 0;

        knightsDevelopment -= BitOperations.countSetBits(board.whiteKnights & WHITE_KNIGHT_SQUARES) * knightOnGoodSquare;
        knightsDevelopment += BitOperations.countSetBits(board.blackKnights & BLACK_KNIGHT_SQUARES) * knightOnGoodSquare;

        boolean early = board.numOfTurns < knightEarlyUntilTurn;
        knightsDevelopment += (board.whiteKnights & BoardParts.BACK_FIVE_RANKS) != 0 && early ? knightOutEarly : 0;
        knightsDevelopment -= (board.blackKnights & BoardParts.FIRST_FIVE_RANKS) != 0 && early ? knightOutEarly : 0;
        return knightsDevelopment;
    }

    private int getBishopsDevelopment(BitBoard board) {
        int bishopsDevelopment = 0;
        bishopsDevelopment += (board.whiteBishops & BoardParts.FIRST_RANK) != 0 ? bishopsHome : 0;
        bishopsDevelopment -= (board.blackBishops & BoardParts.EIGHTH_RANK) != 0 ? bishopsHome : 0;
        return bishopsDevelopment;
    }
}
