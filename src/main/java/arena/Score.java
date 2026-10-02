package arena;

import java.util.List;

/**
 * How one player did in a set of games, with the Elo difference to its opponents and a 95%
 * interval. The interval matters: over 100 games a 55% score is +35 Elo, but anything from about
 * -25 to +95 fits it just as well.
 */
public record Score(String player, int wins, int draws, int losses) {

    /** A score this many standard errors either side of the result gives the 95% interval. */
    private static final double Z95 = 1.96;
    /** The Elo shown for a score of 0% or 100%, which has no finite value. */
    public static final double ELO_LIMIT = 800;

    public static Score of(String player, List<GameRecord> games) {
        int wins = 0;
        int draws = 0;
        int losses = 0;
        for (GameRecord game : games) {
            double points = game.scoreOf(player);
            if (points == 1) {
                wins++;
            } else if (points == 0.5) {
                draws++;
            } else if (points == 0) {
                losses++;
            }
        }
        return new Score(player, wins, draws, losses);
    }

    public int games() {
        return wins + draws + losses;
    }

    /** Points per game, 0 to 1. */
    public double fraction() {
        return games() == 0 ? 0.5 : (wins + 0.5 * draws) / games();
    }

    /** The Elo difference to the opponents that this score implies. */
    public double elo() {
        return elo(fraction());
    }

    /** The low end of the 95% interval of {@link #elo()}. */
    public double eloLow() {
        return elo(fraction() - Z95 * standardError());
    }

    /** The high end of the 95% interval of {@link #elo()}. */
    public double eloHigh() {
        return elo(fraction() + Z95 * standardError());
    }

    /** The standard error of {@link #fraction()}, from the spread of the per-game points. */
    public double standardError() {
        int n = games();
        if (n < 2) {
            return 0.5;
        }
        double mean = fraction();
        double meanOfSquares = (wins + 0.25 * draws) / n;
        return Math.sqrt(Math.max(0, meanOfSquares - mean * mean) / n);
    }

    static double elo(double fraction) {
        if (fraction <= 0) {
            return -ELO_LIMIT;
        }
        if (fraction >= 1) {
            return ELO_LIMIT;
        }
        return Math.max(-ELO_LIMIT, Math.min(ELO_LIMIT, -400 * Math.log10(1 / fraction - 1)));
    }

    @Override
    public String toString() {
        return String.format("%s: +%d =%d -%d (%.1f%%), Elo %+d [%+d, %+d]", player, wins, draws, losses,
                100 * fraction(), Math.round(elo()), Math.round(eloLow()), Math.round(eloHigh()));
    }
}
