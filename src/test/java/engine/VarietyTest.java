package engine;

import ai.BitBoard.BitBoardEvaluate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Game;
import rules.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The engine does not play the same game every time, yet a seed repeats it (Phase 5). */
class VarietyTest {

    private static final int LEVEL = 4; // depth 3 in the opening

    private static ChessMove move(MinimaxEngine engine, String fen) {
        return engine.bestMove(SearchRequest.of(fen, LEVEL));
    }

    private static MinimaxEngine engine(long seed, int variety) {
        return new MinimaxEngine(new Random(seed), MinimaxEngine.TIME_CAP_MS, BitBoardEvaluate.DEFAULT, variety);
    }

    /** The first {@code plies} moves of a game the engine plays against itself. */
    private static List<String> selfPlay(MinimaxEngine engine, int plies) {
        Game game = new Game();
        for (int i = 0; i < plies && !game.status().isGameOver(); i++) {
            game.play(move(engine, game.fen()));
        }
        return game.history();
    }

    @Test
    @DisplayName("Games against itself differ from seed to seed")
    void gamesDiffer() {
        Set<List<String>> games = new HashSet<>();
        for (long seed = 0; seed < 5; seed++) {
            games.add(selfPlay(engine(seed, MinimaxEngine.DEFAULT_VARIETY), 12));
        }
        assertTrue(games.size() >= 3, "only " + games.size() + " different games in 5: " + games);
    }

    @Test
    @DisplayName("The same seed plays the same game")
    void aSeedRepeats() {
        assertEquals(selfPlay(engine(42, MinimaxEngine.DEFAULT_VARIETY), 12),
                selfPlay(engine(42, MinimaxEngine.DEFAULT_VARIETY), 12));
    }

    @Test
    @DisplayName("Without variety the engine plays the search's one best move")
    void noVarietyIsTheBestMove() {
        for (long seed = 0; seed < 3; seed++) {
            assertEquals(MinimaxEngine.searchAtDepth(Position.START_FEN, 3), move(engine(seed, 0), Position.START_FEN));
        }
    }

    @Test
    @DisplayName("Even a large variety never passes up a mate")
    void neverPassesUpAMate() {
        String mateInOne = "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"; // Ra8#
        for (long seed = 0; seed < 10; seed++) {
            assertEquals("a1a8", move(engine(seed, 1000), mateInOne).toUci());
        }
    }

    @Test
    @DisplayName("A large variety still keeps away from losing a piece")
    void staysCloseToTheBest() {
        // a knight attacked by a pawn: moving it away is the only way to stay level
        String fen = "rnbqkbnr/ppp1pppp/8/3p4/4N3/8/PPPPPPPP/R1BQKBNR w KQkq - 0 1";
        for (long seed = 0; seed < 10; seed++) {
            String uci = move(engine(seed, MinimaxEngine.DEFAULT_VARIETY), fen).toUci();
            assertTrue(uci.startsWith("e4"), "seed " + seed + " played " + uci);
        }
    }
}
