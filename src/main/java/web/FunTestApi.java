package web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The blind fun test's ratings (docs/fun-test.md). After a game of a {@code fun-test-*} variant the
 * game screen asks the player "Would you play this again tomorrow?" (1 to 5):
 * <ul>
 *   <li>{@code POST /api/fun-test/ratings} {player, variant, game, rating}: kept, one line each, in
 *       the ratings file; rating the same game again replaces the earlier answer</li>
 *   <li>{@code GET /api/fun-test/ratings}: every rating as given</li>
 *   <li>{@code GET /api/fun-test/summary}: per variant the players, the rated games, the mean rating,
 *       and the players who played it three times or more (the "came back on their own" signal)</li>
 * </ul>
 */
final class FunTestApi {

    /** One answer. {@code game} is the saved game's id, so a game counts once. */
    record Rating(String player, String variant, String game, int rating, String at) {}

    private final Path file;

    FunTestApi(Path file) {
        this.file = file;
    }

    void routes(Javalin app) {
        app.post("/api/fun-test/ratings", this::add);
        app.get("/api/fun-test/ratings", ctx -> {
            JsonArray out = new JsonArray();
            ratings().forEach(r -> out.add(tree(r)));
            ctx.contentType("application/json").result(out.toString());
        });
        app.get("/api/fun-test/summary", ctx -> ctx.contentType("application/json").result(summary(ratings()).toString()));
    }

    private synchronized void add(Context ctx) {
        Rating r;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String player = text(body, "player").strip();
            String variant = text(body, "variant");
            String game = text(body, "game");
            int rating = body.get("rating").getAsInt();
            if (player.isEmpty() || player.length() > 40) {
                throw new IllegalArgumentException("a player's name is 1 to 40 characters");
            }
            if (!variant.startsWith("fun-test-")) {
                throw new IllegalArgumentException("only fun test variants are rated: " + variant);
            }
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("a rating is 1 to 5: " + rating);
            }
            r = new Rating(player, variant, game, rating, Instant.now().toString());
        } catch (RuntimeException e) {
            JsonObject o = new JsonObject();
            o.addProperty("error", String.valueOf(e.getMessage()));
            ctx.status(400).contentType("application/json").result(o.toString());
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, tree(r) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ctx.status(201).contentType("application/json").result(tree(r).toString());
    }

    private static String text(JsonObject body, String key) {
        if (!body.has(key) || body.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing \"" + key + "\"");
        }
        return body.get(key).getAsString();
    }

    /** Every rating, the last answer for a game winning. */
    synchronized List<Rating> ratings() {
        if (!Files.exists(file)) {
            return List.of();
        }
        Map<String, Rating> byGame = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                Rating r = new Rating(o.get("player").getAsString(), o.get("variant").getAsString(),
                        o.get("game").getAsString(), o.get("rating").getAsInt(), o.get("at").getAsString());
                byGame.remove(r.game());
                byGame.put(r.game(), r);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ArrayList<>(byGame.values());
    }

    /** Per variant: players, games, mean rating and the players who played it 3 times or more. */
    static JsonObject summary(List<Rating> ratings) {
        Map<String, List<Rating>> byVariant = new TreeMap<>();
        ratings.forEach(r -> byVariant.computeIfAbsent(r.variant(), k -> new ArrayList<>()).add(r));
        JsonArray variants = new JsonArray();
        byVariant.forEach((variant, list) -> {
            Map<String, Integer> games = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            list.forEach(r -> games.merge(r.player(), 1, Integer::sum));
            JsonObject o = new JsonObject();
            o.addProperty("variant", variant);
            o.addProperty("games", list.size());
            o.addProperty("players", games.size());
            o.addProperty("meanRating", list.stream().mapToInt(Rating::rating).average().orElse(0));
            long third = games.values().stream().filter(n -> n >= 3).count();
            o.addProperty("playersWithThirdGame", third);
            o.addProperty("thirdGameShare", games.isEmpty() ? 0 : third / (double) games.size());
            variants.add(o);
        });
        JsonObject out = new JsonObject();
        out.addProperty("ratings", ratings.size());
        out.add("variants", variants);
        return out;
    }

    private static JsonObject tree(Rating r) {
        JsonObject o = new JsonObject();
        o.addProperty("player", r.player());
        o.addProperty("variant", r.variant());
        o.addProperty("game", r.game());
        o.addProperty("rating", r.rating());
        o.addProperty("at", r.at());
        return o;
    }
}
