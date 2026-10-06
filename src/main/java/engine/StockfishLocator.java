package engine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds the Stockfish executable. Before this class the game only looked at one exact file,
 * {@code engine/stockfish-windows-x86-64.exe} relative to the working directory, so a download
 * with its real name ({@code stockfish-windows-x86-64-avx2.exe}, inside a {@code stockfish/}
 * folder), a jar started from another folder, or {@code apt install stockfish} all went unseen and
 * Levels 8-10 quietly played the built-in engine instead.
 *
 * <p>Search order, first hit wins:
 * <ol>
 *   <li>the {@code -Dstockfish.path=...} system property, then the {@code STOCKFISH_PATH}
 *       environment variable (taken as given, even if the file is missing, so a typo shows up as
 *       "not found" rather than silently picking another Stockfish);</li>
 *   <li>any file named {@code stockfish*} (an {@code .exe} on Windows, an executable file
 *       elsewhere) in an {@code engine/} folder, or one folder below it, next to the working
 *       directory, next to the jar, or one level above the jar (the repository root when the
 *       jar runs from {@code target/});</li>
 *   <li>{@code stockfish} on the {@code PATH};</li>
 *   <li>the usual install locations: {@code /usr/games}, {@code /usr/local/bin},
 *       {@code /opt/homebrew/bin}, {@code /usr/bin}.</li>
 * </ol>
 */
public final class StockfishLocator {

    private static final List<String> SYSTEM_DIRS = List.of("/usr/games", "/usr/local/bin", "/opt/homebrew/bin", "/usr/bin");

    private StockfishLocator() {}

    /** Where Stockfish is on this computer, if anywhere. */
    public static Optional<Path> find() {
        return find("stockfish", "stockfish.path", "STOCKFISH_PATH");
    }

    /**
     * Where the executable called {@code name} is, by the same search: the system property
     * {@code property}, the environment variable {@code env}, a {@code name*} file in an
     * {@code engine/} folder, {@code name} on the PATH. Fairy-Stockfish uses it too.
     */
    static Optional<Path> find(String name, String property, String env) {
        String explicit = System.getProperty(property);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(env);
        }
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(Path.of(explicit));
        }
        List<Path> engineDirs = new ArrayList<>();
        engineDirs.add(Path.of("engine"));
        jarDir().ifPresent(dir -> {
            engineDirs.add(dir.resolve("engine"));
            if (dir.getParent() != null) {
                engineDirs.add(dir.getParent().resolve("engine"));
            }
        });
        engineDirs.add(installDir()); // where the in-game download puts it
        boolean windows = isWindows();
        List<Path> searchDirs = pathDirs();
        if (!windows) {
            SYSTEM_DIRS.forEach(d -> searchDirs.add(Path.of(d)));
        }
        return find(name, engineDirs, searchDirs, windows);
    }

    /**
     * The search itself, with its inputs passed in so tests can point it at a temporary folder:
     * the {@code engine/} folders first, then {@code stockfish} in each of {@code searchDirs}.
     */
    static Optional<Path> find(List<Path> engineDirs, List<Path> searchDirs, boolean windows) {
        return find("stockfish", engineDirs, searchDirs, windows);
    }

    /** As {@link #find(List, List, boolean)} for the executable called {@code name}. */
    static Optional<Path> find(String name, List<Path> engineDirs, List<Path> searchDirs, boolean windows) {
        Set<Path> seen = new LinkedHashSet<>();
        for (Path dir : engineDirs) {
            Path abs = dir.toAbsolutePath().normalize();
            if (seen.add(abs)) {
                Optional<Path> hit = inEngineDir(name, abs, windows);
                if (hit.isPresent()) {
                    return hit;
                }
            }
        }
        for (Path dir : searchDirs) {
            Path candidate = dir.resolve(windows ? name + ".exe" : name);
            if (isRunnable(candidate, windows)) {
                return Optional.of(candidate.toAbsolutePath());
            }
        }
        return Optional.empty();
    }

    /** A {@code name*} executable in {@code dir} or one folder below it, shallowest and then alphabetically first. */
    private static Optional<Path> inEngineDir(String name, Path dir, boolean windows) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(dir, 2)) {
            return files
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(name))
                    .filter(p -> isRunnable(p, windows))
                    .min(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean isRunnable(Path p, boolean windows) {
        if (!Files.isRegularFile(p)) {
            return false;
        }
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return windows ? name.endsWith(".exe") : Files.isExecutable(p) && !name.contains(".");
    }

    private static List<Path> pathDirs() {
        String path = System.getenv("PATH");
        List<Path> dirs = new ArrayList<>();
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    try {
                        dirs.add(Path.of(entry));
                    } catch (RuntimeException e) {
                        // a malformed PATH entry: skip it
                    }
                }
            }
        }
        return dirs;
    }

    /**
     * Where a downloaded Stockfish goes: the {@code engine/} folder of the project, found from the
     * jar (or class folder) by stepping out of {@code target/} and {@code classes/}; the working
     * directory's {@code engine/} if the code's location is unknown. {@link #find()} looks there.
     */
    public static Path installDir() {
        Optional<Path> dir = jarDir();
        if (dir.isEmpty()) {
            return Path.of("engine").toAbsolutePath();
        }
        Path base = dir.get().toAbsolutePath();
        while (base.getParent() != null && Set.of("target", "classes", "test-classes").contains(base.getFileName().toString())) {
            base = base.getParent();
        }
        return base.resolve("engine");
    }

    private static Optional<Path> jarDir() {
        try {
            Path location = Path.of(StockfishLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Optional.ofNullable(Files.isDirectory(location) ? location : location.getParent());
        } catch (URISyntaxException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
