package evolution;

import ai.eval.ParamVector;
import arena.GameRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A population and the games it just played: what {@link Evolution#nextGeneration} decides from.
 * In the games, member {@code i} is the player named {@code String.valueOf(i)}.
 *
 * @param number     0 for the first generation
 * @param population the members, each a full parameter vector
 * @param games      every game this generation played, each with its moves, result and reason
 */
public record Generation(int number, List<ParamVector> population, List<GameRecord> games) {

    public Generation {
        population = List.copyOf(population);
        games = List.copyOf(games);
    }

    public int size() {
        return population.size();
    }

    /** The name member {@code i} plays under. */
    public static String name(int i) {
        return String.valueOf(i);
    }

    /** Points member {@code i} scored: 1 a win, ½ a draw. */
    public double points(int i) {
        String name = name(i);
        return games.stream().mapToDouble(g -> g.scoreOf(name)).filter(p -> !Double.isNaN(p)).sum();
    }

    /** Games member {@code i} played. */
    public int gamesPlayed(int i) {
        String name = name(i);
        return (int) games.stream().filter(g -> g.white().equals(name) || g.black().equals(name)).count();
    }

    /** Points per game of member {@code i}, 0 to 1 (½ if it played none). */
    public double score(int i) {
        int n = gamesPlayed(i);
        return n == 0 ? 0.5 : points(i) / n;
    }

    /** Points member {@code i} scored against member {@code j}, over all their games. */
    public double pointsAgainst(int i, int j) {
        String me = name(i);
        String them = name(j);
        double points = 0;
        for (GameRecord g : games) {
            if (g.white().equals(me) && g.black().equals(them) || g.white().equals(them) && g.black().equals(me)) {
                points += g.scoreOf(me);
            }
        }
        return points;
    }

    /** Member indexes from the best score to the worst (ties: lower index first). */
    public List<Integer> ranking() {
        return new ArrayList<>(IntStream.range(0, size()).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> -score(i)).thenComparing(i -> i))
                .toList());
    }

    /** The member with the best score. */
    public int champion() {
        return ranking().getFirst();
    }
}
