package web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** POST a game, poll until the report is there. */
class AnalysisApiTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;
    private AnalysisApi api;

    @AfterEach
    void stop() {
        if (api != null) {
            api.shutdown();
        }
        if (app != null) {
            app.stop();
        }
    }

    private String start(List<String> command) {
        api = new AnalysisApi(() -> command);
        app = Javalin.create();
        api.routes(app);
        app.start("127.0.0.1", 0);
        return "http://127.0.0.1:" + app.port();
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject get(String url) throws Exception {
        return JsonParser.parseString(http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    @Test
    @DisplayName("Without Stockfish the request is refused with a message saying so")
    void noStockfish() throws Exception {
        String base = start(null);
        HttpResponse<String> r = post(base + "/api/analysis", "{\"moves\":[\"e2e4\"]}");
        assertEquals(503, r.statusCode());
        assertTrue(r.body().contains("\"noStockfish\":true"));
    }

    @Test
    @DisplayName("A game is analysed in the background and its report polled")
    void analyse() throws Exception {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        String base = start(List.of(path));
        HttpResponse<String> r = post(base + "/api/analysis", "{\"moves\":[\"e2e4\",\"e7e5\",\"g1f3\"],\"depth\":8}");
        assertEquals(200, r.statusCode());
        int id = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsInt();
        JsonObject poll;
        long deadline = System.currentTimeMillis() + 30_000;
        do {
            Thread.sleep(100);
            poll = get(base + "/api/analysis/" + id);
        } while (!poll.get("done").getAsBoolean() && System.currentTimeMillis() < deadline);
        assertTrue(poll.get("done").getAsBoolean());
        assertEquals(4, poll.get("progress").getAsInt());
        JsonObject report = poll.getAsJsonObject("report");
        assertEquals(4, report.getAsJsonArray("evals").size());
        assertEquals("Nf3", report.getAsJsonArray("moves").get(2).getAsJsonObject().get("san").getAsString());
        assertTrue(report.getAsJsonObject("white").has("elo"));
    }

    @Test
    @DisplayName("An illegal game comes back as an error, not a report")
    void illegal() throws Exception {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        String base = start(List.of(path));
        int id = JsonParser.parseString(post(base + "/api/analysis", "{\"moves\":[\"e2e5\"]}").body())
                .getAsJsonObject().get("id").getAsInt();
        JsonObject poll;
        do {
            Thread.sleep(50);
            poll = get(base + "/api/analysis/" + id);
        } while (!poll.get("done").getAsBoolean());
        assertTrue(poll.get("error").getAsString().contains("illegal move e2e5"));
    }
}
