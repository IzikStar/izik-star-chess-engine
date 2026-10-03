package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.Position;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stockfish really plays Levels 8-10 (October 2026: the owner's Level 6 beat "Stockfish" at Level
 * 10, because the game never found Stockfish and Levels 8-10 quietly fell back to Level 2).
 */
class StockfishStrengthTest {

    /** UI Level 10 and UI Level 6, on the session's 0-18 scale. */
    private static final int LEVEL_10 = 18;
    private static final int LEVEL_6 = 10;

    private static boolean installed() {
        return Files.isExecutable(Path.of("/usr/games/stockfish")) || System.getProperty("stockfish.path") != null
                || System.getenv("STOCKFISH_PATH") != null;
    }

    @Test
    @DisplayName("When Stockfish is installed, the game's default Stockfish finds it without any setting")
    void defaultEngineFindsStockfish() {
        assumeTrue(installed(), "Stockfish not installed");
        StockfishEngine stockfish = new StockfishEngine();
        try {
            assertNotNull(stockfish.path());
            ChessMove move = stockfish.bestMove(SearchRequest.of(Position.START_FEN, LEVEL_10));
            assertNotNull(move, "Stockfish at " + stockfish.path() + " gave no move");
            assertTrue(stockfish.isAvailable());
        } finally {
            stockfish.close();
        }
    }

    @Test
    @Tag("stress")
    @DisplayName("Stockfish at Level 10 beats the built-in engine at Level 6 with either colour")
    void level10BeatsLevel6() {
        assumeTrue(installed(), "Stockfish not installed");
        EngineSelector engines = new EngineSelector(new MinimaxEngine(), new StockfishEngine());
        try {
            for (boolean stockfishWhite : new boolean[] {true, false}) {
                Game game = new Game(Position.START_FEN);
                List<ChessMove> moves = new ArrayList<>();
                while (!game.status().isGameOver() && moves.size() < 400) {
                    boolean whiteToMove = moves.size() % 2 == 0;
                    int level = whiteToMove == stockfishWhite ? LEVEL_10 : LEVEL_6;
                    ChessMove move = engines.move(new SearchRequest(game.fen(), Position.START_FEN,
                            List.copyOf(moves), level, Cancellation.NONE));
                    assertNotNull(move);
                    game.play(move);
                    moves.add(move);
                }
                assertTrue(engines.stockfishAvailable(), "Stockfish dropped out during the game");
                assertEquals(GameStatus.CHECKMATE, game.status(), "Stockfish as " + (stockfishWhite ? "White" : "Black"));
                boolean whiteWon = moves.size() % 2 == 1;
                assertEquals(stockfishWhite, whiteWon, "the built-in engine mated Stockfish");
            }
        } finally {
            engines.close();
        }
    }
}
