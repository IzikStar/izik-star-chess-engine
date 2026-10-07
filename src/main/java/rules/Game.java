package rules;

import ai.piece.PieceType;
import ai.variant.Variant;
import ai.variant.Variants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A game in progress: the current position plus enough history to answer threefold repetition.
 * A thin, still-headless wrapper over {@link Rules} — FEN in the history list, UCI at the edge.
 *
 * <p>This replaces the two disagreeing repetition trackers in the legacy code
 * ({@code main.savedGames.SavedStatesForDraws}, keyed on a draw-FEN string in the old Swing UI, and
 * {@code ai.BoardStateTracker}, keyed on a Zobrist hash inside the search).
 */
public final class Game {

    private final Variant variant;
    private final List<String> fenHistory = new ArrayList<>();
    private final List<MoveResult> moves = new ArrayList<>();

    public Game() {
        this(Position.START_FEN);
    }

    public Game(String startFen) {
        this(Variants.CHESS, startFen);
    }

    /** A game of {@code variant} from its start position. */
    public Game(Variant variant) {
        this(variant, variant.startFen());
    }

    public Game(Variant variant, String startFen) {
        this.variant = variant;
        fenHistory.add(startFen);
    }

    public Variant variant() {
        return variant;
    }

    public String fen() {
        return fenHistory.get(fenHistory.size() - 1);
    }

    public List<String> history() {
        return List.copyOf(fenHistory);
    }

    public Position position() {
        return Position.fromFen(fen());
    }

    public List<ChessMove> legalMoves() {
        return Rules.legalMoves(variant, fen());
    }

    /** The moves played so far, oldest first. */
    public List<MoveResult> moves() {
        return List.copyOf(moves);
    }

    /**
     * Play a legal move. A promotion without a piece promotes to the first piece the variant lists
     * (a queen in chess).
     *
     * @throws IllegalArgumentException if the move is not legal here
     */
    public MoveResult play(ChessMove move) {
        String before = fen();
        Position pos = Position.fromFen(before);
        if (!move.isPromotion()) {
            char first = firstPromotion(pos, move);
            if (first != 0) {
                move = new ChessMove(move.from(), move.to(), first);
            }
        }
        String after = Rules.applyMove(variant, before, move);
        String san = San.of(variant, before, move);
        boolean enPassant = San.isEnPassant(pos, move);
        char piece = pos.pieceAt(move.from());
        char captured = enPassant
                ? pos.pieceAt(Square.of(Square.file(move.to()), Square.rank8Row(move.from())))
                : pos.pieceAt(move.to());
        boolean castling = Rules.castling(variant, before, move) != null;
        fenHistory.add(after);
        MoveResult result = new MoveResult(move, san, piece, captured, castling, enPassant,
                pos.fullmoveNumber(), before, after, status(), Rules.checkedSquares(variant, after));
        moves.add(result);
        return result;
    }

    public MoveResult play(String uci) {
        return play(ChessMove.fromUci(uci));
    }


    /**
     * The piece {@code move} promotes to when none is named: the first the moving piece lists, if
     * it reaches its last row; otherwise 0.
     */
    private char firstPromotion(Position pos, ChessMove move) {
        char piece = pos.pieceAt(move.from());
        int toRow = Square.rank8Row(move.to());
        boolean lastRow = Character.isUpperCase(piece) ? toRow == 0 : toRow == 7;
        if (!lastRow) {
            return 0;
        }
        for (PieceType type : variant.pieces()) {
            if (type.letter() == Character.toUpperCase(piece) && !type.promotesTo().isEmpty()) {
                return Character.toLowerCase(type.promotesTo().getFirst());
            }
        }
        return 0;
    }

    /** Take back the last move. Returns false at the start of the game. */
    public boolean undo() {
        if (moves.isEmpty()) {
            return false;
        }
        moves.remove(moves.size() - 1);
        fenHistory.remove(fenHistory.size() - 1);
        return true;
    }

    public int plyCount() {
        return moves.size();
    }

    /** How many times the most-repeated position in the history has occurred. */
    public int maxRepetitionCount() {
        Map<String, Integer> seen = new HashMap<>();
        int max = 0;
        for (String fen : fenHistory) {
            int n = seen.merge(Position.fromFen(fen).repetitionKey(), 1, Integer::sum);
            max = Math.max(max, n);
        }
        return max;
    }

    public boolean isThreefoldRepetition() {
        return maxRepetitionCount() >= 3;
    }

    public GameStatus status() {
        GameStatus base = Rules.status(variant, fen());
        if (!base.isGameOver() && variant.repetition() && isThreefoldRepetition()) {
            return GameStatus.DRAW_THREEFOLD;
        }
        return base;
    }
}
