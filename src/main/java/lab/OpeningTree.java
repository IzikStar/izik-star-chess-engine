package lab;

import arena.GameRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The opening tree of a run's games: from a line of moves, which moves the players went on with,
 * how often, how those games ended, and how often each generation chose them. Moves are keyed by
 * the line that reached them (no transpositions). A run starts its games from fixed openings (the
 * chess suite or random moves in a variant), so each branch also counts the games in which the move
 * was part of that opening rather than a player's choice.
 */
public final class OpeningTree {

    /** Plies of each game the tree keeps; deeper moves are not branched. */
    public static final int MAX_PLIES = 30;

    /**
     * One game, cut to its first {@link #MAX_PLIES} moves.
     *
     * @param forced how many of the first moves were the game's fixed opening
     */
    public record Line(int generation, String[] moves, int forced, GameRecord.Result result) {}

    /** What a set of games came to. */
    public record Tally(int games, int whiteWins, int draws, int blackWins) {
        static Tally of(int[] t) {
            return new Tally(t[0], t[1], t[2], t[3]);
        }
    }

    /**
     * A move played from the node.
     *
     * @param forced       games in which the move was part of the fixed opening
     * @param byGeneration games with this move in each generation from the node's first on
     */
    public record Branch(String uci, Tally tally, int forced, int[] byGeneration) {}

    /**
     * The games that reached a line, and the moves played next, most played first.
     *
     * @param firstGeneration the first generation counted; {@code byGeneration} starts there
     * @param byGeneration    games that reached the line in each generation
     */
    public record Node(List<String> path, Tally tally, List<Branch> children, int firstGeneration, int[] byGeneration) {}

    private OpeningTree() {
    }

    /**
     * The node {@code path} leads to, over the games of generations {@code first} to {@code last}
     * (both included).
     */
    public static Node at(List<Line> lines, List<String> path, int first, int last) {
        int span = Math.max(0, last - first + 1);
        int[] tally = new int[4];
        int[] byGeneration = new int[span];
        record Acc(int[] tally, int[] forced, int[] byGeneration) {}
        Map<String, Acc> next = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line.generation() < first || line.generation() > last || !startsWith(line.moves(), path)) {
                continue;
            }
            count(tally, line.result());
            byGeneration[line.generation() - first]++;
            if (line.moves().length > path.size()) {
                Acc acc = next.computeIfAbsent(line.moves()[path.size()], k -> new Acc(new int[4], new int[1], new int[span]));
                count(acc.tally(), line.result());
                if (path.size() < line.forced()) {
                    acc.forced()[0]++;
                }
                acc.byGeneration()[line.generation() - first]++;
            }
        }
        List<Branch> children = new ArrayList<>();
        next.forEach((uci, acc) -> children.add(new Branch(uci, Tally.of(acc.tally()), acc.forced()[0], acc.byGeneration())));
        children.sort(Comparator.comparingInt((Branch b) -> b.tally().games()).reversed().thenComparing(Branch::uci));
        return new Node(List.copyOf(path), Tally.of(tally), children, first, byGeneration);
    }

    /**
     * A game as a line: its first moves, sharing the move strings in {@code moves} so that a run's
     * tens of thousands of games hold only a few thousand distinct strings.
     */
    public static Line line(int generation, List<String> gameMoves, int forced, GameRecord.Result result,
                            Map<String, String> moves) {
        String[] kept = new String[Math.min(MAX_PLIES, gameMoves.size())];
        for (int i = 0; i < kept.length; i++) {
            kept[i] = moves.computeIfAbsent(gameMoves.get(i), m -> m);
        }
        return new Line(generation, kept, Math.min(forced, kept.length), result);
    }

    private static boolean startsWith(String[] moves, List<String> path) {
        if (moves.length < path.size()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            if (!moves[i].equals(path.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static void count(int[] t, GameRecord.Result result) {
        t[0]++;
        t[switch (result) {
            case WHITE_WINS -> 1;
            case DRAW -> 2;
            case BLACK_WINS -> 3;
        }]++;
    }
}
