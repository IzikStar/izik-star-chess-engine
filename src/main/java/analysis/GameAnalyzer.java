package analysis;

import analysis.UciEvaluator.Score;
import analysis.UciEvaluator.Verdict;
import rules.ChessMove;
import rules.GameStatus;
import rules.Rules;
import rules.San;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Analyses a game move by move with a universal engine (Stockfish), the way chess.com's and
 * Lichess's game reports do: an evaluation for every position, how much each move lost, a quality
 * mark per move, and per side an accuracy and a rough Elo estimate. The formulas are in
 * docs/game-analysis.md; the numbers themselves are computed in {@link #report}, which needs no
 * engine, so they can be tested on their own.
 *
 * <p>The rules stay in Java: the moves are checked with {@link Rules} before the engine sees them.
 */
public final class GameAnalyzer {

    public static final int DEFAULT_DEPTH = 12;
    public static final int MIN_DEPTH = 6;
    public static final int MAX_DEPTH = 22;

    /** Centipawns that stand for "won" (a mate, or a finished game) when sizing a loss. */
    static final int DECISIVE_CP = 1000;
    /** The score of a position where the game is already over and someone won. */
    static final int GAME_OVER_CP = 10_000;

    /** How good a move was. */
    public enum Quality { BEST, EXCELLENT, GOOD, INACCURACY, MISTAKE, BLUNDER }

    /** One move's verdict. Losses are from the mover's point of view, never negative. */
    public record MoveReport(String uci, String san, boolean white, String bestUci, String bestSan,
                             int cpLoss, double winChanceLoss, double accuracy, Quality quality) {}

    /** One side's summary. */
    public record SideReport(int moves, double acpl, double accuracy, int elo,
                             int inaccuracies, int mistakes, int blunders) {}

    /** The whole report. {@code evals[i]} is the position after {@code i} plies, White's point of view. */
    public record Report(int depth, List<Score> evals, List<MoveReport> moves, SideReport white, SideReport black) {}

    private GameAnalyzer() {}

    /**
     * Evaluates every position of the game and builds the report.
     *
     * @param progress told how many positions are done, after each one
     * @throws IllegalArgumentException if a move is not legal
     */
    public static Report analyze(UciEvaluator engine, String startFen, List<String> uciMoves, int depth,
                                 IntConsumer progress) throws IOException {
        List<String> fens = new ArrayList<>();
        fens.add(startFen);
        String fen = startFen;
        for (String uci : uciMoves) {
            ChessMove move = ChessMove.fromUci(uci);
            if (!Rules.isLegal(fen, move)) {
                throw new IllegalArgumentException("illegal move " + uci + " in " + fen);
            }
            fen = Rules.applyMove(fen, move);
            fens.add(fen);
        }
        List<Score> evals = new ArrayList<>();
        List<String> best = new ArrayList<>();
        for (int ply = 0; ply < fens.size(); ply++) {
            String f = fens.get(ply);
            boolean whiteToMove = f.split(" ")[1].equals("w");
            Score over = gameOverScore(f, whiteToMove);
            if (over != null) {
                evals.add(over);
                best.add(null);
            } else {
                Verdict v = engine.evaluate(startFen, uciMoves.subList(0, ply), depth, whiteToMove);
                evals.add(v.score());
                best.add(v.bestMove());
            }
            progress.accept(ply + 1);
        }
        return report(depth, fens, uciMoves, evals, best);
    }

    /** The score of a finished position (mate or a rules draw), or null if play goes on. */
    private static Score gameOverScore(String fen, boolean whiteToMove) {
        GameStatus status = Rules.status(fen);
        if (status == GameStatus.CHECKMATE) {
            return Score.cp(whiteToMove ? -GAME_OVER_CP : GAME_OVER_CP);
        }
        if (status.isGameOver()) {
            return Score.cp(0);
        }
        return null;
    }

    /** The numbers, from the evaluations: everything after the engine has spoken. */
    static Report report(int depth, List<String> fens, List<String> uciMoves, List<Score> evals, List<String> best) {
        List<MoveReport> moves = new ArrayList<>();
        for (int i = 0; i < uciMoves.size(); i++) {
            String fen = fens.get(i);
            boolean white = fen.split(" ")[1].equals("w");
            String uci = uciMoves.get(i);
            int sign = white ? 1 : -1;
            int before = sign * clampedCp(evals.get(i));
            int after = sign * clampedCp(evals.get(i + 1));
            int cpLoss = Math.max(0, before - after);
            double winLoss = Math.max(0, winChance(before) - winChance(after));
            String bestUci = best.get(i);
            Quality quality = uci.equals(bestUci) ? Quality.BEST : quality(winLoss);
            String bestSan = bestUci == null ? null : San.of(fen, ChessMove.fromUci(bestUci));
            moves.add(new MoveReport(uci, San.of(fen, ChessMove.fromUci(uci)), white, bestUci, bestSan,
                    cpLoss, round1(winLoss), round1(moveAccuracy(winLoss)), quality));
        }
        return new Report(depth, List.copyOf(evals), moves, side(moves, true), side(moves, false));
    }

    private static SideReport side(List<MoveReport> all, boolean white) {
        List<MoveReport> mine = all.stream().filter(m -> m.white() == white).toList();
        if (mine.isEmpty()) {
            return new SideReport(0, 0, 0, 0, 0, 0, 0);
        }
        double acpl = mine.stream().mapToInt(MoveReport::cpLoss).average().orElse(0);
        double accuracy = mine.stream().mapToDouble(MoveReport::accuracy).average().orElse(0);
        return new SideReport(mine.size(), round1(acpl), round1(accuracy), elo(acpl),
                count(mine, Quality.INACCURACY), count(mine, Quality.MISTAKE), count(mine, Quality.BLUNDER));
    }

    private static int count(List<MoveReport> moves, Quality q) {
        return (int) moves.stream().filter(m -> m.quality() == q).count();
    }

    /** A score as centipawns for sizing losses: mates and finished games count as ±{@link #DECISIVE_CP}. */
    static int clampedCp(Score s) {
        if (s.mate() != null) {
            return s.mate() >= 0 ? DECISIVE_CP : -DECISIVE_CP;
        }
        return Math.max(-DECISIVE_CP, Math.min(DECISIVE_CP, s.cp()));
    }

    /** Lichess's winning chances, 0-100, for the side whose score {@code cp} is. */
    static double winChance(int cp) {
        return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
    }

    /** Lichess's accuracy of one move, 0-100, from the winning chances it gave away. */
    static double moveAccuracy(double winChanceLoss) {
        return Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * winChanceLoss) - 3.1669));
    }

    /** Quality from winning chances lost: under 2 excellent, 10 good, 20 inaccuracy, 30 mistake, else a blunder. */
    static Quality quality(double winChanceLoss) {
        if (winChanceLoss < 2) {
            return Quality.EXCELLENT;
        }
        if (winChanceLoss < 10) {
            return Quality.GOOD;
        }
        if (winChanceLoss < 20) {
            return Quality.INACCURACY;
        }
        if (winChanceLoss < 30) {
            return Quality.MISTAKE;
        }
        return Quality.BLUNDER;
    }

    /**
     * A rough playing strength from average centipawn loss: {@code 3100 · e^(−0.01 · ACPL)},
     * between 400 and 3000. 20 ACPL ≈ 2540, 50 ≈ 1880, 100 ≈ 1140.
     */
    static int elo(double acpl) {
        return (int) Math.round(Math.max(400, Math.min(3000, 3100 * Math.exp(-0.01 * acpl))));
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }
}
