package web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.StockfishEngine;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The download button's API: before, during and after, and the game's Stockfish switching over. */
class StockfishApiTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private JsonObject call(String url, boolean post) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        HttpRequest r = post ? b.POST(HttpRequest.BodyPublishers.noBody()).build() : b.build();
        return JsonParser.parseString(http.send(r, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    @Test
    @DisplayName("Install: not available, then downloading, then the game's Stockfish uses the new file and browsers are told")
    void install(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        StockfishEngine engine = new StockfishEngine(List.of());
        AtomicInteger refreshes = new AtomicInteger();
        java.nio.file.Path exe = dir.resolve("stockfish-test");
        // a stand-in for the download: the real one is tested in engine.StockfishInstallerTest
        StockfishApi api = new StockfishApi(() -> engine, refreshes::incrementAndGet, (assets, progress) -> {
            assertEquals(List.of("x.zip"), assets);
            progress.update(50, 100);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return exe;
        }, () -> List.of("x.zip"));
        app = Javalin.create();
        api.routes(app);
        app.start("127.0.0.1", 0);
        String base = "http://127.0.0.1:" + app.port();

        JsonObject before = call(base + "/api/stockfish", false);
        assertFalse(before.get("available").getAsBoolean());
        assertTrue(before.get("canInstall").getAsBoolean());

        call(base + "/api/stockfish/install", true);
        JsonObject s;
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            Thread.sleep(20);
            s = call(base + "/api/stockfish", false);
        } while (s.get("installing").getAsBoolean() && System.currentTimeMillis() < deadline);
        assertTrue(s.get("available").getAsBoolean());
        assertEquals(exe.toString(), s.get("path").getAsString());
        assertEquals(exe.toString(), engine.path());
        assertEquals(1, refreshes.get());
    }
}
