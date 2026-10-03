package game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.Position;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The saved games folder: one file per game, read back exactly, newest first. */
class GameArchiveTest {

    @TempDir
    Path dir;

    private static SavedGame game(String id, Instant started, String result) {
        return new SavedGame(id, started, started.plusSeconds(60), new GameConfig(GameConfig.Mode.HUMAN_VS_ENGINE, false, 6),
                "runs/a.db", 3, "Champion of A, generation 3", new TimeControl(180_000, 2_000), 170_000L, 150_500L,
                Position.START_FEN, List.of("e2e4", "e7e5"), result, result == null ? null : "White won by resignation", "1. e4 e5 *", "tuned");
    }

    @Test
    @DisplayName("A saved game reads back field for field")
    void roundTrip() {
        GameArchive archive = new GameArchive(dir.resolve("games"));
        SavedGame saved = game("g1", Instant.parse("2026-10-03T20:00:00Z"), "1-0");
        archive.save(saved);
        assertEquals(saved, archive.get("g1").orElseThrow());

        SavedGame untimed = new SavedGame("g2", saved.started(), saved.updated(), GameConfig.defaults(), null, null, null,
                TimeControl.NONE, null, null, Position.START_FEN, List.of("d2d4"), null, null, "1. d4 *", null);
        archive.save(untimed);
        assertEquals(untimed, archive.get("g2").orElseThrow());
        assertEquals("classic", untimed.weights()); // saved without weights: played before tuned existed
        assertFalse(untimed.finished());
    }

    @Test
    @DisplayName("The list is newest first, skips unreadable files, and a save replaces the game")
    void listAndReplace() throws Exception {
        GameArchive archive = new GameArchive(dir);
        assertEquals(List.of(), new GameArchive(dir.resolve("none")).list());
        archive.save(game("old", Instant.parse("2026-10-01T10:00:00Z"), null));
        archive.save(game("new", Instant.parse("2026-10-03T10:00:00Z"), null));
        archive.save(game("old", Instant.parse("2026-10-01T10:00:00Z"), "0-1"));
        Files.writeString(dir.resolve("broken.json"), "{not json");
        List<SavedGame> games = archive.list();
        assertEquals(List.of("new", "old"), games.stream().map(SavedGame::id).toList());
        assertEquals("0-1", games.get(1).result());
        assertFalse(Files.exists(dir.resolve("old.json.tmp")));
    }

    @Test
    @DisplayName("Delete removes a game; ids that could leave the folder are refused")
    void deleteAndIds() {
        GameArchive archive = new GameArchive(dir);
        archive.save(game("x", Instant.now(), null));
        assertTrue(archive.delete("x"));
        assertFalse(archive.delete("x"));
        assertTrue(archive.get("../x").isEmpty());
        assertFalse(archive.delete("../x"));
        assertThrows(IllegalArgumentException.class, () -> archive.save(game("a/b", Instant.now(), null)));
        String id = archive.newId(Instant.parse("2026-10-03T20:00:00Z"));
        assertTrue(id.matches("2026-10-0\\d_\\d\\d-\\d\\d-\\d\\d_[0-9a-z]{1,4}"), id);
    }
}
