package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the game finds Stockfish: the download's own file names, one folder deep, and the PATH. */
class StockfishLocatorTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Finds the Windows download under its real name, inside the zip's stockfish/ folder")
    void windowsDownloadName() throws IOException {
        Path engine = Files.createDirectories(dir.resolve("engine/stockfish"));
        Files.writeString(engine.resolve("README.md"), "docs");
        Path exe = Files.writeString(engine.resolve("stockfish-windows-x86-64-avx2.exe"), "");
        assertEquals(Optional.of(exe.toAbsolutePath()),
                StockfishLocator.find(List.of(dir.resolve("engine")), List.of(), true));
    }

    @Test
    @DisplayName("On Linux and macOS takes an executable stockfish* file, never an archive or a text file")
    void unixDownloadName() throws IOException {
        Path engine = Files.createDirectories(dir.resolve("engine"));
        Files.writeString(engine.resolve("stockfish-ubuntu-x86-64-avx2.tar"), "");
        Files.writeString(engine.resolve("stockfish-not-executable"), "");
        assertEquals(Optional.empty(), StockfishLocator.find(List.of(engine), List.of(), false));
        Path bin = Files.writeString(engine.resolve("stockfish-ubuntu-x86-64-avx2"), "");
        assertTrue(bin.toFile().setExecutable(true));
        assertEquals(Optional.of(bin.toAbsolutePath()), StockfishLocator.find(List.of(engine), List.of(), false));
    }

    @Test
    @DisplayName("Falls back to stockfish on the PATH")
    void onThePath() throws IOException {
        Path bin = Files.createDirectories(dir.resolve("bin"));
        Path sf = Files.writeString(bin.resolve("stockfish.exe"), "");
        assertEquals(Optional.of(sf.toAbsolutePath()),
                StockfishLocator.find(List.of(dir.resolve("no-engine-dir")), List.of(bin), true));
    }

    @Test
    @DisplayName("An engine with no executable reports itself unavailable from the start")
    void nothingFound() {
        StockfishEngine none = new StockfishEngine(List.of());
        assertFalse(none.isAvailable());
        assertEquals(null, none.path());
    }
}
