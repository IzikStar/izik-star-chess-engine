package lab;

import ai.eval.ChessEvaluate;
import ai.board.Boards;
import ai.eval.ParamSchema;
import ai.eval.ParamSpec;
import ai.eval.ParamVector;
import arena.ExternalEngine;
import arena.GameRecord;
import arena.Opening;
import arena.Player;
import arena.Tournament;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;
import rules.Rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Texel tuning (docs/phase-5-research.md §8.3): fits the evaluation's weights so that
 * {@code sigmoid(eval)} predicts the results of many games' quiet positions.
 *
 * <p>The evaluation is a weighted sum of features ({@link ChessEvaluate#features}), blended
 * between middlegame and endgame by the material left, so the predicted score is linear in the
 * weights before the sigmoid. The fit is then a logistic regression: one best answer, wherever it
 * starts. It runs full-batch gradient descent (Adam) on the mean squared error between result and
 * prediction, with a light pull towards the starting weights so squares and features the data
 * never shows stay where they were.
 *
 * <p>The three "until turn N" settings are not fitted: they gate features on and off, which is
 * not a weight. They stay as in the starting weights.
 *
 * <p>Positions come from {@link #selfPlay}: Stockfish against itself from the arena's openings
 * plus a few random moves, keeping the positions where the move played was not a capture or a
 * promotion and the side to move was not in check.
 */
public final class Texel {

    private static final ParamSchema SCHEMA = ChessEvaluate.SCHEMA;
    private static final int NAMED = ChessEvaluate.NAMED;
    private static final int PST = ChessEvaluate.PST_SIZE;
    /** Index of the first piece-square parameter: after every named feature's mg/eg and the gates. */
    private static final int PST_START = 2 * NAMED + 3;

    private Texel() {}

    // ---- data ---------------------------------------------------------------------------------

    /** Plies at the start of a self-play game that are not kept (the opening and the random moves). */
    static final int SKIP_PLIES = 12;

    /**
     * Plays {@code games} games of Stockfish against itself, {@code nodes} a move, and writes their
     * quiet positions as {@link TrainingExport} writes them ({@code fen,result,score}, from White's
     * side). Returns the lines written.
     */
    public static int selfPlay(int games, long nodes, int threads, long seed, Writer out,
                               Consumer<String> progress) throws IOException {
        ExternalEngine stockfish = ExternalEngine.stockfishFull(nodes);
        Player white = Player.external("sf-white", stockfish);
        Player black = Player.external("sf-black", stockfish);
        List<Opening> suite = Opening.suite();
        Random random = new Random(seed);
        List<Tournament.Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            Opening base = suite.get(i % suite.size());
            fixtures.add(new Tournament.Fixture(white, black, randomized(base, 2 + random.nextInt(5), random), i));
        }
        AtomicInteger done = new AtomicInteger();
        List<GameRecord> records = Tournament.play(fixtures, new Tournament.Settings(300, threads, seed),
                g -> {
                    int n = done.incrementAndGet();
                    if (n % 100 == 0) {
                        progress.accept(n + "/" + games + " games");
                    }
                });
        return TrainingExport.write(ai.variant.Variants.CHESS, records, SKIP_PLIES, out);
    }

    /** {@code opening} followed by {@code extra} random legal moves (fewer if the game ends). */
    static Opening randomized(Opening opening, int extra, Random random) {
        Game game = new Game();
        opening.moves().forEach(game::play);
        List<String> moves = new ArrayList<>(opening.moves());
        for (int i = 0; i < extra && game.status() != GameStatus.CHECKMATE && !game.status().isGameOver(); i++) {
            List<ChessMove> legal = game.legalMoves();
            ChessMove move = legal.get(random.nextInt(legal.size()));
            game.play(move);
            moves.add(move.toUci());
        }
        return new Opening(opening.name() + " +" + extra, moves);
    }

    /** The positions of {@code record} where the side to move was not in check and played a quiet move. */
    static List<String> quietPositions(GameRecord record) {
        Game game = new Game();
        List<String> quiet = new ArrayList<>();
        for (String uci : record.moves()) {
            MoveResult m = game.play(uci);
            if (game.plyCount() > SKIP_PLIES && !m.isCapture() && !m.isPromotion() && !Rules.isCheck(m.fenBefore())) {
                quiet.add(m.fenBefore());
            }
        }
        return quiet;
    }

    // ---- positions as features ---------------------------------------------------------------

    /**
     * One position: its non-zero features (index into the {@code NAMED + PST} feature list, value
     * from White's side), its phase and its result.
     */
    record Sample(int[] index, int[] value, int phase, double result) {}

    /** Reads {@code fen,result[,score]} lines (a header line is skipped) as features; the score is not used. */
    static List<Sample> load(Path file) throws IOException {
        List<Sample> samples = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(file)) {
            for (String line; (line = in.readLine()) != null; ) {
                String[] parts = line.split(",");
                if (parts.length < 2 || line.startsWith("fen,")) {
                    continue;
                }
                samples.add(sample(parts[0], Double.parseDouble(parts[1])));
            }
        }
        return samples;
    }

    static Sample sample(String fen, double result) {
        ChessEvaluate.Features f = ChessEvaluate.CLASSIC.features(Boards.fromFen(fen));
        int[] values = f.values();
        int nonZero = 0;
        for (int v : values) {
            if (v != 0) nonZero++;
        }
        int[] index = new int[nonZero];
        int[] value = new int[nonZero];
        int k = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0) {
                index[k] = i;
                value[k++] = -values[i]; // features are Black minus White; the result is White's
            }
        }
        return new Sample(index, value, f.phase(), result);
    }

    // ---- the model ----------------------------------------------------------------------------

    /** Parameter index of feature {@code i}'s middlegame weight (endgame: {@link #egParam}). */
    static int mgParam(int feature) {
        return feature < NAMED ? 2 * feature : PST_START + (feature - NAMED);
    }

    static int egParam(int feature) {
        return feature < NAMED ? 2 * feature + 1 : PST_START + PST + (feature - NAMED);
    }

    /** White's score in centipawns with weights {@code w}, as the evaluation computes it. */
    static double eval(Sample s, double[] w) {
        double mg = 0, eg = 0;
        for (int k = 0; k < s.index().length; k++) {
            mg += s.value()[k] * w[mgParam(s.index()[k])];
            eg += s.value()[k] * w[egParam(s.index()[k])];
        }
        return (mg * s.phase() + eg * (ChessEvaluate.MAX_PHASE - s.phase())) / ChessEvaluate.MAX_PHASE;
    }

    /** Expected score for White at {@code eval} centipawns. */
    static double sigmoid(double eval, double k) {
        return 1 / (1 + Math.pow(10, -k * eval / 400));
    }

    /** Mean squared error of the predictions with weights {@code w}. */
    static double error(List<Sample> samples, double[] w, double k) {
        double sum = 0;
        for (Sample s : samples) {
            double d = s.result() - sigmoid(eval(s, w), k);
            sum += d * d;
        }
        return sum / samples.size();
    }

    /** The scale {@code k} that fits the starting weights best (a golden-section search). */
    static double fitK(List<Sample> samples, double[] w) {
        double lo = 0.1, hi = 3;
        double phi = (Math.sqrt(5) - 1) / 2;
        for (int i = 0; i < 40; i++) {
            double a = hi - phi * (hi - lo), b = lo + phi * (hi - lo);
            if (error(samples, w, a) < error(samples, w, b)) {
                hi = b;
            } else {
                lo = a;
            }
        }
        return (lo + hi) / 2;
    }

    /** What a tuning run did. */
    public record Result(ParamVector params, double k, double startError, double endError,
                         double holdOutStart, double holdOutEnd, int samples) {}

    /**
     * Fits the weights to {@code samples}, starting from {@code start}. A tenth of the samples is
     * held out to report the error on positions the fit did not see.
     */
    public static Result tune(List<Sample> all, ParamVector start, int iterations, double regularization,
                              Consumer<String> progress) {
        List<Sample> train = new ArrayList<>();
        List<Sample> holdOut = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            (i % 10 == 9 ? holdOut : train).add(all.get(i));
        }
        double[] w = new double[SCHEMA.size()];
        double[] w0 = new double[SCHEMA.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = w0[i] = start.get(i);
        }
        boolean[] fitted = new boolean[w.length];
        for (int f = 0; f < NAMED + PST; f++) {
            fitted[mgParam(f)] = fitted[egParam(f)] = true;
        }
        double k = fitK(train, w);
        double startError = error(train, w, k);
        double holdOutStart = error(holdOut, w, k);
        progress.accept(String.format("k %.3f, error %.5f (held out %.5f), %d positions", k, startError,
                holdOutStart, all.size()));

        // Adam
        double rate = 2, beta1 = 0.9, beta2 = 0.999, eps = 1e-8;
        double[] m = new double[w.length];
        double[] v = new double[w.length];
        double[] grad = new double[w.length];
        double ln10 = Math.log(10);
        for (int t = 1; t <= iterations; t++) {
            java.util.Arrays.fill(grad, 0);
            for (Sample s : train) {
                double p = sigmoid(eval(s, w), k);
                // d(result - p)^2/d eval = -2 (result - p) p (1 - p) k ln10 / 400
                double g = -2 * (s.result() - p) * p * (1 - p) * k * ln10 / 400 / train.size();
                double mgShare = g * s.phase() / ChessEvaluate.MAX_PHASE;
                double egShare = g * (ChessEvaluate.MAX_PHASE - s.phase()) / ChessEvaluate.MAX_PHASE;
                for (int j = 0; j < s.index().length; j++) {
                    grad[mgParam(s.index()[j])] += mgShare * s.value()[j];
                    grad[egParam(s.index()[j])] += egShare * s.value()[j];
                }
            }
            for (int i = 0; i < w.length; i++) {
                if (!fitted[i]) {
                    continue;
                }
                double gi = grad[i] + 2 * regularization * (w[i] - w0[i]);
                m[i] = beta1 * m[i] + (1 - beta1) * gi;
                v[i] = beta2 * v[i] + (1 - beta2) * gi * gi;
                double mHat = m[i] / (1 - Math.pow(beta1, t));
                double vHat = v[i] / (1 - Math.pow(beta2, t));
                ParamSpec spec = SCHEMA.spec(i);
                w[i] = Math.max(spec.min(), Math.min(spec.max(), w[i] - rate * mHat / (Math.sqrt(vHat) + eps)));
            }
            if (t % 100 == 0 || t == iterations) {
                progress.accept(String.format("iteration %d: error %.5f (held out %.5f)", t, error(train, w, k),
                        error(holdOut, w, k)));
            }
        }
        centerTables(w);
        int[] rounded = new int[w.length];
        for (int i = 0; i < w.length; i++) {
            rounded[i] = (int) Math.round(w[i]);
        }
        ParamVector result = new ParamVector(SCHEMA, rounded);
        double[] r = new double[w.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = result.get(i);
        }
        return new Result(result, k, startError, error(train, r, k), holdOutStart, error(holdOut, r, k), all.size());
    }

    /**
     * Moves each piece-square table's average into the piece's material value (the king has
     * none: its average is dropped, since every position has one king a side). The evaluation is
     * unchanged; the tables then read as "better or worse than average here".
     */
    static void centerTables(double[] w) {
        String[] material = {"material.pawn", "material.knight", "material.bishop", "material.rook", "material.queen", null};
        for (int phase = 0; phase < 2; phase++) {
            for (int piece = 0; piece < 6; piece++) {
                int from = PST_START + phase * PST + piece * 32;
                // pawns never stand on their first or last rank: average over the squares they can use
                int first = piece == 0 ? 4 : 0, last = piece == 0 ? 28 : 32;
                double mean = 0;
                for (int i = first; i < last; i++) {
                    mean += w[from + i];
                }
                mean /= last - first;
                for (int i = 0; i < 32; i++) {
                    w[from + i] = i >= first && i < last ? w[from + i] - mean : 0;
                }
                if (material[piece] != null) {
                    w[SCHEMA.indexOf(material[piece] + (phase == 0 ? ".mg" : ".eg"))] += mean;
                }
            }
        }
    }
}
