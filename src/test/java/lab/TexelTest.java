package lab;

import ai.eval.ChessEvaluate;
import ai.board.Boards;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.Opening;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.Game;
import rules.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexelTest {

    private static final String[] FENS = {
            "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            "r2q1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PP3PPP/R2Q1RK1 b - - 3 10",
            "8/5pk1/6p1/3P4/2p5/2P3P1/5PK1/8 w - - 0 40",
            "4r1k1/1b3ppp/p7/1p6/3N4/1P3Q2/P4PPP/3R2K1 b - - 1 25",
    };

    private static double[] weights(ParamVector p) {
        double[] w = new double[p.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = p.get(i);
        }
        return w;
    }

    @Test
    @DisplayName("The fit's model scores a position as the evaluation does, from White's side")
    void modelMatchesTheEvaluation() {
        ParamVector p = ChessEvaluate.CLASSIC.params().with("pst.knight.d4.mg", 25).with("pieces.bishopPair.eg", 40)
                .with("mobility.rook.mg", 3).with("pst.pawn.c6.eg", 30);
        ChessEvaluate eval = new ChessEvaluate(p);
        for (String fen : FENS) {
            double model = Texel.eval(Texel.sample(fen, 0.5), weights(p));
            int engine = eval.evaluate(Boards.fromFen(fen), 0);
            assertEquals(engine, model, 1.0, fen);
        }
    }

    @Test
    @DisplayName("Moving a table's average into the material value leaves every score as it was")
    void centeringKeepsScores() {
        Random random = new Random(3);
        double[] w = weights(ChessEvaluate.CLASSIC.params());
        for (int i = 2 * ChessEvaluate.NAMED + 3; i < w.length; i++) {
            w[i] = random.nextInt(80) - 20;
        }
        double[] centered = w.clone();
        Texel.centerTables(centered);
        for (String fen : FENS) {
            Texel.Sample s = Texel.sample(fen, 0.5);
            assertEquals(Texel.eval(s, w), Texel.eval(s, centered), 1e-6, fen);
        }
    }

    @Test
    @DisplayName("Tuning on positions where an extra knight wins raises the knight's value")
    void tuningLearns() {
        // White a knight up wins, everything else even: the fit should value the knight more
        List<Texel.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            samples.add(Texel.sample("4k3/pppppppp/8/8/8/8/PPPPPPPP/1N2K3 w - - 0 30", 1));
            samples.add(Texel.sample("4k3/pppppppp/8/8/8/8/PPPPPPPP/4K3 w - - 0 30", 0.5));
        }
        ParamVector start = ChessEvaluate.CLASSIC.params().with("material.knight.eg", 50);
        Texel.Result r = Texel.tune(samples, start, 200, 0, s -> {});
        assertTrue(r.endError() < r.startError());
        assertTrue(r.params().get("material.knight.eg") > 50, "knight " + r.params().get("material.knight.eg"));
    }

    @Test
    @DisplayName("Kept positions are past the opening, not in check, and followed by a quiet move")
    void quietPositions() {
        Opening line = Texel.randomized(Opening.suite().getFirst(), 4, new Random(1));
        assertEquals(Opening.suite().getFirst().moves().size() + 4, line.moves().size());
        Game game = new Game();
        line.moves().forEach(game::play);
        List<String> moves = new ArrayList<>(line.moves());
        for (int i = 0; i < 30 && !game.status().isGameOver(); i++) {
            String uci = game.legalMoves().getFirst().toUci();
            game.play(uci);
            moves.add(uci);
        }
        List<String> quiet = Texel.quietPositions(new GameRecord("a", "b", "x", moves, GameRecord.Result.DRAW, "", 0));
        assertFalse(quiet.isEmpty());
        quiet.forEach(fen -> assertFalse(Rules.isCheck(fen), fen));
    }
}
