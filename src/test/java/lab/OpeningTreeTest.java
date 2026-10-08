package lab;

import arena.GameRecord.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningTreeTest {

    private final Map<String, String> moves = new HashMap<>();

    private OpeningTree.Line line(int generation, String moves, int forced, Result result) {
        return OpeningTree.line(generation, List.of(moves.split(" ")), forced, result, this.moves);
    }

    private final List<OpeningTree.Line> games = List.of(
            line(0, "e2e4 e7e5 g1f3", 1, Result.WHITE_WINS),
            line(0, "e2e4 c7c5", 1, Result.BLACK_WINS),
            line(1, "e2e4 e7e5 f1c4", 1, Result.DRAW),
            line(1, "d2d4 d7d5", 1, Result.DRAW),
            line(2, "e2e4 e7e5 g1f3", 0, Result.WHITE_WINS));

    @Test
    @DisplayName("The root counts every game and lists the first moves, most played first")
    void root() {
        OpeningTree.Node root = OpeningTree.at(games, List.of(), 0, 2);
        assertEquals(new OpeningTree.Tally(5, 2, 2, 1), root.tally());
        assertEquals(List.of("e2e4", "d2d4"), root.children().stream().map(OpeningTree.Branch::uci).toList());
        OpeningTree.Branch e4 = root.children().getFirst();
        assertEquals(new OpeningTree.Tally(4, 2, 1, 1), e4.tally());
        assertEquals(3, e4.forced()); // the last game chose 1.e4 itself
        assertArrayEquals(new int[] {2, 1, 1}, e4.byGeneration());
        assertArrayEquals(new int[] {2, 2, 1}, root.byGeneration());
    }

    @Test
    @DisplayName("A line counts only the games that reached it, in the generations asked for")
    void lineAndGenerations() {
        OpeningTree.Node node = OpeningTree.at(games, List.of("e2e4", "e7e5"), 1, 2);
        assertEquals(2, node.tally().games());
        assertEquals(List.of("f1c4", "g1f3"), node.children().stream().map(OpeningTree.Branch::uci).toList());
        assertEquals(0, node.children().getFirst().forced());
        assertArrayEquals(new int[] {0, 1}, node.children().get(1).byGeneration());
        assertEquals(1, node.firstGeneration());
    }

    @Test
    @DisplayName("A line no game reached is empty, and lines share their move strings")
    void emptyAndShared() {
        OpeningTree.Node node = OpeningTree.at(games, List.of("a2a3"), 0, 2);
        assertEquals(0, node.tally().games());
        assertTrue(node.children().isEmpty());
        assertSame(games.get(0).moves()[0], games.get(4).moves()[0]);
        List<String> longGame = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            longGame.add("g1f3");
        }
        assertEquals(OpeningTree.MAX_PLIES, OpeningTree.line(0, longGame, 50, Result.DRAW, moves).moves().length);
    }
}
