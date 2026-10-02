package rules;

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

    private final List<String> fenHistory = new ArrayList<>();
    private final List<MoveResult> moves = new ArrayList<>();

    public Game() {
        this(Position.START_FEN);
    }

    public Game(String startFen) {
        fenHistory.add(startFen);
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
        return Rules.legalMoves(fen());
    }

    /** The moves played so far, oldest first. */
    public List<MoveResult> moves() {
        return List.copyOf(moves);
    }

    /**
     * Play a legal move. A promotion without a piece promotes to a queen.
     *
     * @throws IllegalArgumentException if the move is not legal here
     */
    public MoveResult play(ChessMove move) {
        String before = fen();
        Position pos = Position.fromFen(before);
        if (isPromotionMove(pos, move) && !move.isPromotion()) {
            move = new ChessMove(move.from(), move.to(), 'q');
        }
        String after = Rules.applyMove(before, move);
        String san = San.of(before, move);
        boolean enPassant = San.isEnPassant(pos, move);
        char piece = pos.pieceAt(move.from());
        char captured = enPassant
                ? pos.pieceAt(Square.of(Square.file(move.to()), Square.rank8Row(move.from())))
                : pos.pieceAt(move.to());
        boolean castling = Character.toUpperCase(piece) == 'K'
                && Math.abs(Square.file(move.from()) - Square.file(move.to())) == 2;
        fenHistory.add(after);
        MoveResult result = new MoveResult(move, san, piece, captured, castling, enPassant,
                pos.fullmoveNumber(), before, after, status());
        moves.add(result);
        return result;
    }

    public MoveResult play(String uci) {
        return play(ChessMove.fromUci(uci));
    }

    /** True if {@code move} is a pawn reaching the last rank (it needs a promotion piece). */
    public static boolean isPromotionMove(Position pos, ChessMove move) {
        char piece = pos.pieceAt(move.from());
        int toRow = Square.rank8Row(move.to());
        return (piece == 'P' && toRow == 0) || (piece == 'p' && toRow == 7);
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
        GameStatus base = Rules.status(fen());
        if (!base.isGameOver() && isThreefoldRepetition()) {
            return GameStatus.DRAW_THREEFOLD;
        }
        return base;
    }
}
