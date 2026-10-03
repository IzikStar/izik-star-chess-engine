package analysis;

import analysis.GameAnalyzer.MoveReport;
import analysis.GameAnalyzer.Quality;
import analysis.GameAnalyzer.Report;
import analysis.UciEvaluator.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The game report: its formulas on made-up evaluations, then a real game judged by Stockfish. */
class GameAnalyzerTest {

    private static final List<String> SCHOLARS_MATE = List.of("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7");

    private static List<String> fens(List<String> moves) {
        List<String> fens = new ArrayList<>();
        String fen = Position.START_FEN;
        fens.add(fen);
        for (String m : moves) {
            fen = Rules.applyMove(fen, ChessMove.fromUci(m));
            fens.add(fen);
        }
        return fens;
    }

    @Test
    @DisplayName("Winning chances, move accuracy and the Elo estimate follow the published formulas")
    void formulas() {
        assertEquals(50.0, GameAnalyzer.winChance(0), 1e-9);
        assertEquals(59.1, GameAnalyzer.winChance(100), 0.1);
        assertEquals(100.0, GameAnalyzer.moveAccuracy(0), 0.01);
        assertEquals(63.6, GameAnalyzer.moveAccuracy(10), 0.1);
        assertEquals(2538, GameAnalyzer.elo(20));
        assertEquals(1880, GameAnalyzer.elo(50));
        assertEquals(3000, GameAnalyzer.elo(0));
        assertEquals(400, GameAnalyzer.elo(500));
        assertEquals(Quality.EXCELLENT, GameAnalyzer.quality(1.9));
        assertEquals(Quality.GOOD, GameAnalyzer.quality(9.9));
        assertEquals(Quality.INACCURACY, GameAnalyzer.quality(10));
        assertEquals(Quality.MISTAKE, GameAnalyzer.quality(25));
        assertEquals(Quality.BLUNDER, GameAnalyzer.quality(30));
    }

    @Test
    @DisplayName("A move's loss is from the mover's side, mates count as 1000 centipawns, and gains are no loss")
    void lossesFromTheMoversSide() {
        List<String> moves = List.of("e2e4", "e7e5", "g1f3");
        List<Score> evals = List.of(Score.cp(30), Score.cp(40), Score.cp(20), Score.mate(-3));
        List<String> best = java.util.Arrays.asList("e2e4", "d7d5", "b1c3", null);
        Report r = GameAnalyzer.report(12, fens(moves), moves, evals, best);
        MoveReport e4 = r.moves().get(0);
        assertEquals(Quality.BEST, e4.quality());
        assertEquals(0, e4.cpLoss()); // the eval went up for White
        MoveReport e5 = r.moves().get(1);
        assertEquals(0, e5.cpLoss()); // for Black the eval went from -40 to -20: a gain
        MoveReport nf3 = r.moves().get(2);
        assertEquals(1020, nf3.cpLoss()); // +20 to a mate against White (-1000)
        assertEquals(Quality.BLUNDER, nf3.quality());
        assertEquals("Nc3", nf3.bestSan());
        assertEquals(2, r.white().moves());
        assertEquals(510.0, r.white().acpl());
        assertEquals(1, r.white().blunders());
    }

    @Test
    @DisplayName("An illegal move is refused before the engine sees it")
    void illegalMove() {
        assertThrows(IllegalArgumentException.class,
                () -> GameAnalyzer.analyze(null, Position.START_FEN, List.of("e2e5"), 8, n -> {}));
    }

    @Test
    @DisplayName("Real Stockfish (when installed): in Scholar's mate, ...Nf6 is a blunder and Qxf7# ends it")
    void scholarsMate() throws Exception {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        List<Integer> progress = new ArrayList<>();
        Report r;
        try (UciEvaluator sf = new UciEvaluator(List.of(path))) {
            r = GameAnalyzer.analyze(sf, Position.START_FEN, SCHOLARS_MATE, 10, progress::add);
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), progress);
        assertEquals(8, r.evals().size());
        MoveReport nf6 = r.moves().get(5);
        assertEquals("Nf6", nf6.san());
        assertEquals(Quality.BLUNDER, nf6.quality());
        assertEquals("Qxf7#", r.moves().get(6).san());
        assertEquals(Quality.BEST, r.moves().get(6).quality());
        assertEquals(GameAnalyzer.GAME_OVER_CP, r.evals().get(7).cp());
        assertTrue(r.evals().get(6).mate() != null && r.evals().get(6).mate() > 0, "White mates in one before Qxf7#");
        assertTrue(r.white().elo() > r.black().elo());
    }
}
