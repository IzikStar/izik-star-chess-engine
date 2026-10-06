package engine;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds Fairy-Stockfish, the Stockfish fork that plays variants (antichess, king of the hill,
 * three-check and made-up ones), the yardstick for games Stockfish does not know. The search is
 * {@link StockfishLocator}'s: the {@code -Dfairy.path=...} system property, the
 * {@code FAIRY_STOCKFISH_PATH} environment variable, any executable {@code fairy-stockfish*} file in
 * an {@code engine/} folder (or one folder below), then {@code fairy-stockfish} on the PATH. It is
 * never downloaded by the game; engine/README.md says where to get it.
 */
public final class FairyStockfishLocator {

    /** The file name the search looks for, and the start of a downloaded file's name. */
    public static final String NAME = "fairy-stockfish";

    /** Where the release builds are, for messages and engine/README.md. */
    public static final String DOWNLOAD = "https://github.com/fairy-stockfish/Fairy-Stockfish/releases";

    private FairyStockfishLocator() {}

    /** Where Fairy-Stockfish is on this computer, if anywhere. */
    public static Optional<Path> find() {
        return StockfishLocator.find(NAME, "fairy.path", "FAIRY_STOCKFISH_PATH");
    }
}
