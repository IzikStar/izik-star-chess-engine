package web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The blind fun test's ratings: kept, one per game, and summed up per variant. */
class FunTestApiTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private Javalin app;

    @TempDir
    Path dir;

    @AfterEach
    void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private String start() {
        app = Javalin.create();
        new FunTestApi(dir.resolve("fun-test-ratings.jsonl")).routes(app);
        app.start("127.0.0.1", 0);
        return "http://127.0.0.1:" + app.port();
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String rating(String player, String variant, String game, int rating) {
        return "{\"player\":\"" + player + "\",\"variant\":\"" + variant + "\",\"game\":\"" + game + "\",\"rating\":" + rating + "}";
    }

    @Test
    @DisplayName("Ratings are summed up per variant; a re-rated game counts once; three games is a comeback")
    void summary() throws Exception {
        String url = start() + "/api/fun-test/ratings";
        assertEquals(201, post(url, rating("Dana", "fun-test-a", "g1", 2)).statusCode());
        assertEquals(201, post(url, rating("Dana", "fun-test-a", "g1", 4)).statusCode()); // changed her mind
        assertEquals(201, post(url, rating("dana", "fun-test-a", "g2", 5)).statusCode());
        assertEquals(201, post(url, rating("Dana", "fun-test-a", "g3", 3)).statusCode());
        assertEquals(201, post(url, rating("Avi", "fun-test-a", "g4", 1)).statusCode());
        assertEquals(201, post(url, rating("Avi", "fun-test-b", "g5", 5)).statusCode());

        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url.replace("ratings", "summary"))).build(),
                HttpResponse.BodyHandlers.ofString());
        JsonObject summary = JsonParser.parseString(r.body()).getAsJsonObject();
        assertEquals(5, summary.get("ratings").getAsInt());
        JsonObject a = summary.getAsJsonArray("variants").get(0).getAsJsonObject();
        assertEquals("fun-test-a", a.get("variant").getAsString());
        assertEquals(4, a.get("games").getAsInt());
        assertEquals(2, a.get("players").getAsInt());
        assertEquals(3.25, a.get("meanRating").getAsDouble(), 1e-9);
        assertEquals(1, a.get("playersWithThirdGame").getAsInt());
    }

    @Test
    @DisplayName("Only fun test variants, a name and a 1-5 rating are accepted")
    void refuses() throws Exception {
        String url = start() + "/api/fun-test/ratings";
        assertEquals(400, post(url, rating("Dana", "chess", "g1", 3)).statusCode());
        assertEquals(400, post(url, rating(" ", "fun-test-a", "g1", 3)).statusCode());
        assertEquals(400, post(url, rating("Dana", "fun-test-a", "g1", 6)).statusCode());
        assertEquals(400, post(url, "not json").statusCode());
    }
}
