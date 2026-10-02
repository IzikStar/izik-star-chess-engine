package evolution;

import ai.BitBoard.BitBoardEvaluate;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.GameRecord.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationTest {

    private static GameRecord game(int white, int black, Result result) {
        return new GameRecord(Generation.name(white), Generation.name(black), "x", List.of(), result, "test", 0);
    }

    private static final ParamVector D = BitBoardEvaluate.SCHEMA.defaults();

    @Test
    @DisplayName("Points, games, head-to-head and ranking")
    void scores() {
        Generation g = new Generation(0, List.of(D, D, D), List.of(
                game(0, 1, Result.WHITE_WINS), game(1, 0, Result.DRAW),
                game(1, 2, Result.WHITE_WINS), game(2, 1, Result.BLACK_WINS),
                game(0, 2, Result.DRAW), game(2, 0, Result.DRAW)));
        assertEquals(2.5, g.points(0));
        assertEquals(2.5, g.points(1));
        assertEquals(1.0, g.points(2));
        assertEquals(4, g.gamesPlayed(0));
        assertEquals(1.5, g.pointsAgainst(0, 1));
        assertEquals(0.5, g.pointsAgainst(1, 0));
        assertEquals(List.of(0, 1, 2), g.ranking()); // a tie keeps the lower index first
        assertEquals(0, g.champion());
    }

    @Test
    @DisplayName("The default pairings are a round robin")
    void roundRobin() {
        Evolution e = new RandomMutationExample();
        assertEquals(List.of(new Pairing(0, 1), new Pairing(0, 2), new Pairing(0, 3),
                        new Pairing(1, 2), new Pairing(1, 3), new Pairing(2, 3)),
                e.pairings(List.of(D, D, D, D), new Random(1)));
    }
}
