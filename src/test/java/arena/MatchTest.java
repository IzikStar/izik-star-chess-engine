package arena;

import ai.BitBoard.BitBoardEvaluate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTest {

    private static final Opening ITALIAN = Opening.suite().getFirst();
    private static final Player A = Player.of("a", BitBoardEvaluate.DEFAULT, 2, 2);
    private static final Player B = Player.of("b", BitBoardEvaluate.DEFAULT, 2, 2);

    @Test
    @DisplayName("The same players, opening and seed play the same game")
    void aSeedRepeatsTheGame() {
        assertEquals(Match.play(A, B, ITALIAN, 80, 7), Match.play(A, B, ITALIAN, 80, 7));
    }

    @Test
    @DisplayName("Another seed plays another game")
    void seedsDiffer() {
        assertNotEquals(Match.play(A, B, ITALIAN, 80, 7).moves(), Match.play(A, B, ITALIAN, 80, 8).moves());
    }

    @Test
    @DisplayName("A game still going at the ply cap is a draw")
    void plyCapIsADraw() {
        GameRecord game = Match.play(A, B, ITALIAN, 20, 1);
        assertEquals(20, game.plies());
        assertEquals(GameRecord.Result.DRAW, game.result());
        assertEquals("PLY_CAP", game.reason());
        assertEquals(ITALIAN.moves(), game.moves().subList(0, ITALIAN.moves().size()));
    }

    @Test
    @DisplayName("Checkmate is scored for the side that gave it")
    void mateIsScored() {
        // after 1.f3 e5 2.g4 White is lost in one: ...Qh4#
        Opening fool = new Opening("fool's mate", List.of("f2f3", "e7e5", "g2g4", "a7a6", "a2a3"));
        GameRecord game = Match.play(A, B, fool, 300, 1);
        assertEquals(List.of("f2f3", "e7e5", "g2g4", "a7a6", "a2a3", "d8h4"), game.moves());
        assertEquals(GameRecord.Result.BLACK_WINS, game.result());
        assertEquals("CHECKMATE", game.reason());
        assertEquals(1.0, game.scoreOf("b"));
        assertEquals(0.0, game.scoreOf("a"));
    }

    @Test
    @DisplayName("Each opening is played once with each player as White")
    void coloursAreSwapped() {
        List<Opening> openings = Opening.suite().subList(0, 3);
        List<GameRecord> games = Tournament.match(A, B, openings, new Tournament.Settings(10, 2, 1), g -> { });
        assertEquals(6, games.size());
        for (int i = 0; i < openings.size(); i++) {
            GameRecord first = games.get(2 * i);
            GameRecord second = games.get(2 * i + 1);
            assertEquals(openings.get(i).name(), first.opening());
            assertEquals(openings.get(i).name(), second.opening());
            assertEquals(List.of("a", "b"), List.of(first.white(), first.black()));
            assertEquals(List.of("b", "a"), List.of(second.white(), second.black()));
        }
    }

    @Test
    @DisplayName("A tournament repeats itself whatever the number of threads")
    void threadsDoNotChangeTheGames() {
        List<Opening> openings = Opening.suite().subList(0, 4);
        assertEquals(Tournament.match(A, B, openings, new Tournament.Settings(40, 1, 3), g -> { }),
                Tournament.match(A, B, openings, new Tournament.Settings(40, 4, 3), g -> { }));
    }

    @Test
    @DisplayName("Searching deeper wins the match")
    void deeperIsStronger() {
        Player deep = Player.of("depth 3", BitBoardEvaluate.DEFAULT, 3, 2);
        Player shallow = Player.of("depth 1", BitBoardEvaluate.DEFAULT, 1, 2);
        List<GameRecord> games = Tournament.match(deep, shallow, Opening.suite().subList(0, 6),
                new Tournament.Settings(200, 4, 1), g -> { });
        Score score = Score.of("depth 3", games);
        assertTrue(score.fraction() > 0.7, score.toString());
    }
}
