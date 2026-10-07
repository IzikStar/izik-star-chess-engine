package engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads Stockfish from its official GitHub releases into the {@code engine/} folder, so a
 * player needs no manual setup: one click in the browser. Only the executable is kept from the
 * archive (a zip on Windows, a tar elsewhere).
 *
 * <p>Each platform has a list of builds, fastest first. A build that does not start on this
 * computer (an AVX2 build on an older CPU, say) is deleted and the next one is tried.
 *
 * <p>The release is pinned ({@link #RELEASE}) rather than "latest", because Stockfish renames its
 * downloads from time to time and a pinned release keeps working.
 */
public final class StockfishInstaller {

    /** A Stockfish release whose download names are known. */
    public static final String RELEASE = "sf_17.1";
    static final String BASE_URL = "https://github.com/official-stockfish/Stockfish/releases/download/" + RELEASE + "/";

    /** Told the bytes downloaded so far and the total (-1 while unknown). */
    public interface Progress {
        void update(long done, long total);
    }

    private final String baseUrl;
    private final Path installDir;
    private final Predicate<Path> works;

    /** Installs into {@link StockfishLocator#installDir()} from the official releases. */
    public StockfishInstaller() {
        this(BASE_URL, StockfishLocator.installDir(), StockfishInstaller::speaksUci);
    }

    /**
     * @param baseUrl    where the archives are, ending in {@code /}
     * @param installDir the {@code engine/} folder to install into
     * @param works      whether an extracted executable runs on this computer
     */
    StockfishInstaller(String baseUrl, Path installDir, Predicate<Path> works) {
        this.baseUrl = baseUrl;
        this.installDir = installDir;
        this.works = works;
    }

    /** The archives to try on this computer, best first; empty if Stockfish has no build for it. */
    public static List<String> assetsForThisComputer() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean x86 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm = arch.equals("aarch64") || arch.equals("arm64");
        if (os.startsWith("windows") && x86) {
            return List.of("stockfish-windows-x86-64-avx2.zip", "stockfish-windows-x86-64-sse41-popcnt.zip");
        }
        if (os.startsWith("mac")) {
            return arm ? List.of("stockfish-macos-m1-apple-silicon.tar") : List.of("stockfish-macos-x86-64-avx2.tar");
        }
        if (os.startsWith("linux") && x86) {
            return List.of("stockfish-ubuntu-x86-64-avx2.tar", "stockfish-ubuntu-x86-64-sse41-popcnt.tar");
        }
        return List.of();
    }

    /** Downloads and unpacks the first of {@code assets} that runs here; returns the executable. */
    public Path install(List<String> assets, Progress progress) throws IOException {
        if (assets.isEmpty()) {
            throw new IOException("Stockfish has no ready-made build for this computer; see engine/README.md.");
        }
        Files.createDirectories(installDir);
        IOException last = null;
        for (String asset : assets) {
            Path archive = Files.createTempFile("stockfish", asset.endsWith(".zip") ? ".zip" : ".tar");
            try {
                download(URI.create(baseUrl + asset), archive, progress);
                Path exe = extract(archive, asset.endsWith(".zip"));
                if (works.test(exe)) {
                    return exe;
                }
                Files.deleteIfExists(exe);
                last = new IOException(asset + " does not run on this computer");
            } catch (IOException e) {
                last = e;
            } finally {
                Files.deleteIfExists(archive);
            }
        }
        throw last;
    }

    private static void download(URI uri, Path to, Progress progress) throws IOException {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("downloading " + uri + " failed: HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("content-length").orElse(-1);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(to)) {
            byte[] buf = new byte[1 << 16];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                progress.update(done, total);
            }
        }
    }

    /** The archive's Stockfish executable, written to {@code installDir/stockfish/}. */
    private Path extract(Path archive, boolean zip) throws IOException {
        try (InputStream in = Files.newInputStream(archive)) {
            return zip ? fromZip(in) : fromTar(in);
        }
    }

    private Path fromZip(InputStream in) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                Optional<Path> target = target(e.getName(), e.isDirectory());
                if (target.isPresent()) {
                    return write(zip, target.get());
                }
            }
        }
        throw new IOException("no Stockfish executable in the download");
    }

    /** A plain (ustar) tar: 512-byte headers, each file padded to 512 bytes. */
    private Path fromTar(InputStream in) throws IOException {
        byte[] header = new byte[512];
        while (in.readNBytes(header, 0, 512) == 512 && header[0] != 0) {
            String name = field(header, 0, 100);
            String prefix = field(header, 345, 155);
            if (!prefix.isEmpty()) {
                name = prefix + "/" + name;
            }
            long size = Long.parseLong(field(header, 124, 12).isEmpty() ? "0" : field(header, 124, 12), 8);
            char type = (char) header[156];
            boolean file = type == '0' || type == 0;
            Optional<Path> target = file ? target(name, false) : Optional.empty();
            if (target.isPresent()) {
                return write(new java.io.ByteArrayInputStream(in.readNBytes((int) size)), target.get());
            }
            long skip = (size + 511) / 512 * 512;
            in.skipNBytes(skip);
        }
        throw new IOException("no Stockfish executable in the download");
    }

    private static String field(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.US_ASCII).trim();
    }

    /** Where an archive entry goes, if it is the executable: {@code stockfish/stockfish-<build>[.exe]}. */
    private Optional<Path> target(String entry, boolean directory) {
        String[] parts = entry.replace('\\', '/').split("/");
        if (directory || parts.length != 2) {
            return Optional.empty();
        }
        String name = parts[1];
        String lower = name.toLowerCase(Locale.ROOT);
        boolean exe = lower.startsWith("stockfish-") && (lower.endsWith(".exe") || !lower.contains("."));
        return exe ? Optional.of(installDir.resolve("stockfish").resolve(name)) : Optional.empty();
    }

    private static Path write(InputStream in, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        target.toFile().setExecutable(true);
        return target;
    }

    /** Whether {@code exe} starts and answers the UCI handshake. */
    static boolean speaksUci(Path exe) {
        Process p = null;
        try {
            p = new ProcessBuilder(exe.toString()).redirectErrorStream(true).start();
            try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write("uci\nquit\n");
            }
            // "quit" ends it after the handshake; a build this CPU cannot run dies and closes the stream
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.trim().equals("uciok")) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            if (p != null) {
                try {
                    if (!p.waitFor(5, TimeUnit.SECONDS)) {
                        p.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    p.destroyForcibly();
                }
            }
        }
    }
}
