package cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cloudflare D1 through its HTTP API: {@code POST /accounts/{account}/d1/database/{database}/query}
 * with {@code {"sql": ..., "params": [...]}}. Each call is one statement.
 */
public final class D1 implements Sql {

    private final CloudConfig config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public D1(CloudConfig config) {
        this.config = config;
    }

    @Override
    public List<JsonObject> query(String sql, Object... params) {
        JsonObject body = new JsonObject();
        body.addProperty("sql", sql);
        JsonArray p = new JsonArray();
        for (Object o : params) {
            if (o == null) {
                p.add((JsonElement) null);
            } else if (o instanceof Number n) {
                p.add(n);
            } else if (o instanceof Boolean b) {
                p.add(b ? 1 : 0);
            } else {
                p.add(o.toString());
            }
        }
        body.add("params", p);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.apiBase() + "/accounts/" + config.account()
                        + "/d1/database/" + config.database() + "/query"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SqlException("cannot reach Cloudflare: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SqlException("interrupted", e);
        }
        JsonObject answer;
        try {
            answer = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new SqlException("Cloudflare answered HTTP " + response.statusCode() + " without JSON",
                    response.statusCode() == 429);
        }
        if (response.statusCode() != 200 || !answer.has("success") || !answer.get("success").getAsBoolean()) {
            String errors = answer.has("errors") ? answer.get("errors").toString() : "";
            String lower = errors.toLowerCase(Locale.ROOT);
            boolean limit = response.statusCode() == 429 || lower.contains("limit") || lower.contains("quota");
            throw new SqlException("D1 refused (HTTP " + response.statusCode() + "): " + errors, limit);
        }
        List<JsonObject> rows = new ArrayList<>();
        JsonArray results = answer.getAsJsonArray("result");
        if (results != null && !results.isEmpty()) {
            JsonObject first = results.get(0).getAsJsonObject();
            if (first.has("results") && first.get("results").isJsonArray()) {
                first.getAsJsonArray("results").forEach(r -> rows.add(r.getAsJsonObject()));
            }
        }
        return rows;
    }
}
