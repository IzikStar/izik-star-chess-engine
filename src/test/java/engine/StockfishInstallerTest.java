package engine;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one-click download, against a local server laid out like Stockfish's GitHub releases. */
class StockfishInstallerTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final Map<String, byte[]> files = new HashMap<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Serves {@code files} under /releases/ behind a redirect, as GitHub does. */
    private String serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download/", ex -> {
            ex.getResponseHeaders().add("Location", "/files/" + ex.getRequestURI().getPath().substring("/download/".length()));
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/files/", ex -> {
            byte[] body = files.get(ex.getRequestURI().getPath().substring("/files/".length()));
            if (body == null) {
                ex.sendResponseHeaders(404, -1);
            } else {
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/download/";
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** A minimal ustar archive of regular files. */
    private static byte[] tar(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (var e : entries.entrySet()) {
            byte[] data = e.getValue().getBytes(StandardCharsets.UTF_8);
            byte[] h = new byte[512];
            put(h, 0, e.getKey());
            put(h, 100, "0000755");
            put(h, 124, String.format("%011o", data.length));
            h[156] = '0';
            put(h, 257, "ustar");
            out.write(h);
            out.write(data);
            out.write(new byte[(512 - data.length % 512) % 512]);
        }
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static void put(byte[] h, int at, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, h, at, b.length);
    }

    private static Map<String, String> release(String exe) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("stockfish/README.md", "readme");
        m.put("stockfish/src/stockfish-notes", "source, not the program");
        m.put("stockfish/" + exe, "the program");
        return m;
    }

    @Test
    @DisplayName("Windows: unpacks only the .exe from the zip into engine/stockfish/, with progress")
    void windowsZip() throws IOException {
        files.put("stockfish-windows-x86-64-avx2.zip", zip(release("stockfish-windows-x86-64-avx2.exe")));
        Path engine = dir.resolve("engine");
        List<Long> progress = new ArrayList<>();
        Path exe = new StockfishInstaller(serve(), engine, p -> true)
                .install(List.of("stockfish-windows-x86-64-avx2.zip"), (done, total) -> progress.add(done));
        assertEquals(engine.resolve("stockfish/stockfish-windows-x86-64-avx2.exe"), exe);
        assertEquals("the program", Files.readString(exe));
        assertFalse(Files.exists(engine.resolve("stockfish/README.md")));
        assertFalse(progress.isEmpty());
        // and the game finds it there
        assertEquals(exe.toAbsolutePath(), StockfishLocator.find(List.of(engine), List.of(), true).orElseThrow());
    }

    @Test
    @DisplayName("Linux and macOS: unpacks the program from the tar")
    void unixTar() throws IOException {
        files.put("stockfish-ubuntu-x86-64-avx2.tar", tar(release("stockfish-ubuntu-x86-64-avx2")));
        Path exe = new StockfishInstaller(serve(), dir, p -> true)
                .install(List.of("stockfish-ubuntu-x86-64-avx2.tar"), (d, t) -> { });
        assertEquals(dir.resolve("stockfish/stockfish-ubuntu-x86-64-avx2"), exe);
        assertEquals("the program", Files.readString(exe));
    }

    @Test
    @DisplayName("A build this computer cannot run is deleted and the next one is tried")
    void fallsBackToTheNextBuild() throws IOException {
        files.put("fast.zip", zip(release("stockfish-windows-x86-64-avx2.exe")));
        files.put("safe.zip", zip(release("stockfish-windows-x86-64-sse41-popcnt.exe")));
        Path exe = new StockfishInstaller(serve(), dir, p -> p.toString().contains("sse41"))
                .install(List.of("fast.zip", "safe.zip"), (d, t) -> { });
        assertTrue(exe.toString().endsWith("stockfish-windows-x86-64-sse41-popcnt.exe"));
        assertFalse(Files.exists(dir.resolve("stockfish/stockfish-windows-x86-64-avx2.exe")));
    }

    @Test
    @DisplayName("A missing download is an error, not a silent success")
    void missingDownload() throws IOException {
        String base = serve();
        IOException e = assertThrows(IOException.class,
                () -> new StockfishInstaller(base, dir, p -> true).install(List.of("nothing.zip"), (d, t) -> { }));
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
    }

    @Test
    @DisplayName("Every platform with a release gets a list of builds, and the pinned release is real")
    void assets() {
        assertTrue(StockfishInstaller.BASE_URL.startsWith("https://github.com/official-stockfish/Stockfish/releases/download/"));
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.startsWith("windows") || os.startsWith("linux") || os.startsWith("mac")) {
            assertFalse(StockfishInstaller.assetsForThisComputer().isEmpty());
        }
    }
}
