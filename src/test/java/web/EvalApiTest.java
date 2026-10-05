package web;

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

/** One position in, its score out: what the live evaluation bar asks after every move. */
class EvalApiTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;
    private EvalApi api;

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
        api = new EvalApi(() -> command);
        app = Javalin.create();
        api.routes(app);
        app.start("127.0.0.1", 0);
        return "http://127.0.0.1:" + app.port();
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Without Stockfish the request is refused with a message saying so")
    void noStockfish() throws Exception {
        HttpResponse<String> r = post(start(null) + "/api/eval", "{\"moves\":[\"e2e4\"]}");
        assertEquals(503, r.statusCode());
        assertTrue(r.body().contains("\"noStockfish\":true"));
    }

    @Test
    @DisplayName("A finished game is scored without an engine: mate wins outright")
    void mateNeedsNoEngine() throws Exception {
        HttpResponse<String> r = post(start(null) + "/api/eval", "{\"moves\":[\"f2f3\",\"e7e5\",\"g2g4\",\"d8h4\"]}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"cp\":-"), r.body());
    }

    @Test
    @DisplayName("An illegal move is refused")
    void illegal() throws Exception {
        assertEquals(400, post(start(null) + "/api/eval", "{\"moves\":[\"e2e5\"]}").statusCode());
    }

    @Test
    @DisplayName("Stockfish scores the position from White's side, and again for the next one")
    void scores() throws Exception {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        String base = start(List.of(path));
        // 1.e4 e5 2.Qh5 Qh4?? and White to move takes the queen
        HttpResponse<String> r = post(base + "/api/eval", "{\"moves\":[\"e2e4\",\"e7e5\",\"d1h5\",\"d8h4\"]}");
        assertEquals(200, r.statusCode());
        int cp = com.google.gson.JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("score").get("cp").getAsInt();
        assertTrue(cp > 500, "White to move takes the queen: " + r.body());
        r = post(base + "/api/eval", "{\"moves\":[]}");
        assertEquals(200, r.statusCode(), "the same engine answers again");
    }
}
