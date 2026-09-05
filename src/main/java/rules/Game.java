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
 * ({@code main.savedGames.SavedStatesForDraws}, keyed on a draw-FEN string in the UI, and
 * {@code ai.BoardStateTracker}, keyed on a Zobrist hash inside the search).
 */
public final class Game {

    private final List<String> fenHistory = new ArrayList<>();

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

    public List<ChessMove> legalMoves() {
        return Rules.legalMoves(fen());
    }

    public void play(ChessMove move) {
        fenHistory.add(Rules.applyMove(fen(), move));
    }

    public void play(String uci) {
        play(ChessMove.fromUci(uci));
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
