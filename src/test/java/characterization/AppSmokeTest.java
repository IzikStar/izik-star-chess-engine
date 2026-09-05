package characterization;

import ai.BitBoard.BitBoard;
import ai.BoardState;
import main.Move;
import main.savedGames.LoadGame;
import main.savedGames.SaveGame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pieces.Piece;

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
 * ({@code BoardState} + {@code Move} + {@code main.CheckScanner}), the real engine board
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
     * Play Scholar's Mate move by move through the object-oriented path a human move would
     * take, asserting the status transitions in-progress -> check -> checkmate. Exercises
     * move execution, capture, and the check/mate detection the UI consults.
     */
    @Test
    @DisplayName("A full scripted game reaches checkmate through the OO rules path")
    void scriptedGameToCheckmate() {
        BoardState b = oo(START);

        // 1. e4 e5  2. Bc4 Bc5  3. Qh5 Nc6  4. Qxf7#
        // cols: a..h = 0..7 ; rows: rank 8..1 = 0..7 ; White home rows 6-7.
        applyMove(b, 4, 6, 4, 4); // e2-e4
        applyMove(b, 4, 1, 4, 3); // e7-e5
        applyMove(b, 5, 7, 2, 4); // Bf1-c4
        applyMove(b, 5, 0, 2, 3); // Bf8-c5
        applyMove(b, 3, 7, 7, 3); // Qd1-h5
        assertEquals(1, b.getAccurateStatus(), "still in progress before the mate");
        applyMove(b, 1, 0, 2, 2); // Nb8-c6
        applyMove(b, 7, 3, 5, 1); // Qh5xf7#  (captures the f7 pawn)

        assertEquals(Integer.MAX_VALUE, b.getAccurateStatus(),
                "Qxf7 is checkmate -> getAccurateStatus() == Integer.MAX_VALUE");
        assertEquals(0, b.getStatus(), "game is over -> getStatus() == 0");
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
        List<String> fens = new ArrayList<>();
        fens.add(START);
        BoardState b = oo(START);
        applyMove(b, 4, 6, 4, 4);
        fens.add(b.convertPiecesToFEN());
        applyMove(b, 4, 1, 4, 3);
        fens.add(b.convertPiecesToFEN());

        Path file = dir.resolve("game.txt");
        new SaveGame().saveGameToFile(fens, file.toString());
        assertTrue(Files.exists(file), "save wrote the file");

        List<String> loaded = new LoadGame().loadGameFromFile(file.toString());
        assertEquals(fens, loaded, "loaded FEN list equals the saved one");
        assertNotEquals(loaded.get(0), loaded.get(1), "the game actually advanced between saved states");
    }

    // ---- helpers ----------------------------------------------------------

    private static void applyMove(BoardState b, int fromCol, int fromRow, int toCol, int toRow) {
        Piece p = b.getPiece(fromCol, fromRow);
        assertTrue(p != null, "no piece at (" + fromCol + "," + fromRow + ")");
        Move m = new Move(b, p, toCol, toRow);
        assertTrue(b.isValidMove(m),
                "move (" + fromCol + "," + fromRow + ")->(" + toCol + "," + toRow + ") should be legal");
        b.makeMove(m);
    }
}
