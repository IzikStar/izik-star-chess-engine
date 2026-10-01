package characterization;

import ai.BitBoard.BitBoard;
import main.savedGames.LoadGame;
import main.savedGames.SaveGame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.Game;
import rules.GameStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end "does a whole game still run" smoke test — the automated stand-in for the
 * manual play-through the refactor guide left owed after Phase 0 / Phase 1.
 *
 * <p>Headless, no Swing, no synthetic input. It drives the real rules core
 * ({@code rules.Game}, the API the UI's game session drives), the real engine board
 * representation ({@code BitBoard}, which the minimax search runs on), and the real
 * persistence classes ({@code SaveGame} / {@code LoadGame}) through complete games.
 *
 * <p>Tagged {@code "smoke"} and excluded from the default {@code mvn test} run because a
 * full random game is slower than a unit test; run with {@code mvn test -Psmoke}.
 *
 * <p>Phase 1 was a pure deletion — the packaged jar is byte-for-byte identical to the
 * pre-Phase-1 jar except for the removed dead classes (see docs/phase-1-notes.md §2), so
 * this exists mainly as a permanent regression asset for the phases that follow.
 */
@Tag("smoke")
class AppSmokeTest extends CharacterizationTestBase {

    /**
     * Play Scholar's Mate move by move through the game API a human move takes, asserting the
     * status transitions in-progress -> checkmate. Exercises move execution, capture, and the
     * check/mate detection the UI consults.
     */
    @Test
    @DisplayName("A full scripted game reaches checkmate through the game API")
    void scriptedGameToCheckmate() {
        Game g = new Game(START);
        for (String m : new String[]{"e2e4", "e7e5", "f1c4", "f8c5", "d1h5"}) {
            g.play(m);
        }
        assertEquals(GameStatus.IN_PROGRESS, g.status(), "still in progress before the mate");
        g.play("b8c6");
        var mate = g.play("h5f7"); // Qxf7#  (captures the f7 pawn)

        assertEquals(GameStatus.CHECKMATE, g.status());
        assertEquals("Qxf7#", mate.san());
        assertEquals('p', mate.captured());
        assertTrue(g.status().isGameOver());
    }

    /**
     * Play a complete pseudo-random game entirely in the engine's bitboard representation,
     * from the opening position to a terminal status, with a fixed seed so it is
     * deterministic. Proves the engine's move generation + game-over detection survive a
     * whole game without throwing.
     */
    @Test
    @DisplayName("A full random game in the bitboard engine terminates cleanly")
    void randomGameInBitboardEngine() {
        Random rnd = new Random(20260905L);
        BitBoard b = bit(START);

        int ply = 0;
        final int cap = 4000; // generous; the current per-ply 50-move counter ends games far sooner
        int status = b.getStatus();
        while (status == 1 && ply < cap) {
            List<BitBoard> next = b.getNextStates();
            assertTrue(!next.isEmpty(), "status==1 implies at least one legal move (ply " + ply + ")");
            b = next.get(rnd.nextInt(next.size()));
            status = b.getStatus();
            ply++;
        }

        assertTrue(ply < cap, "random game should reach a terminal status within " + cap + " plies (got " + ply + ")");
        assertTrue(status == 0 || status == Integer.MAX_VALUE || status == Integer.MIN_VALUE,
                "terminal status must be draw/stalemate (0) or a mate (+/-MAX_VALUE), was " + status);
        assertTrue(ply > 0, "at least one move was played");
    }

    /** The persistence limb: a FEN move-list survives a SaveGame -> LoadGame round-trip. */
    @Test
    @DisplayName("SaveGame / LoadGame round-trips a game's FEN list unchanged")
    void saveLoadRoundTrip(@TempDir Path dir) throws IOException {
        Game g = new Game(START);
        g.play("e2e4");
        g.play("e7e5");
        List<String> fens = new ArrayList<>(g.history());

        Path file = dir.resolve("game.txt");
        new SaveGame().saveGameToFile(fens, file.toString());
        assertTrue(Files.exists(file), "save wrote the file");

        List<String> loaded = new LoadGame().loadGameFromFile(file.toString());
        assertEquals(fens, loaded, "loaded FEN list equals the saved one");
        assertNotEquals(loaded.get(0), loaded.get(1), "the game actually advanced between saved states");
    }
}
