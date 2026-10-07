package web;

import ai.variant.Variant;
import ai.variant.VariantJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import game.VariantStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lab.VariantHealth;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

/**
 * The variant health check over HTTP (Phase 6 R5d), for the designer. One check runs at a time; a
 * new one stops the last.
 * <ul>
 *   <li>{@code POST /api/health} {variant (the whole variant, saved or not), games, depth, and optionally
 *       whiteDepth, blackDepth, randomPlies, maxPlies, seed, threads (0 = automatic), variety (centipawns),
 *       engine ("built-in" or "fairy-stockfish"), fairyNodes}: starts a check; 400 {error} when the
 *       variant or the settings cannot be played</li>
 *   <li>{@code GET /api/health}: {fairyInstalled, cores, running, variantId, variantName, games, depth,
 *       settings, done, report?, error?}; {fairyInstalled, cores, running: false} before any check. The
 *       report keeps its summary fields and adds {@code details} ({@link VariantHealth.Details})</li>
 *   <li>{@code DELETE /api/health}: stops the running check</li>
 * </ul>
 */
final class HealthApi {

    /** Whether Fairy-Stockfish is on this computer, looked up once. */
    private static final boolean FAIRY_INSTALLED = arena.FairyStockfish.installed();

    /** One check: its variant, settings, progress and outcome. */
    private static final class Job {
        final Variant variant;
        final VariantHealth.Settings settings;
        volatile int done;
        volatile boolean cancelled;
        volatile VariantHealth.Report report;
        volatile String error;

        Job(Variant variant, VariantHealth.Settings settings) {
            this.variant = variant;
            this.settings = settings;
        }

        boolean running() {
            return report == null && error == null && !cancelled;
        }
    }

    private volatile Job job;

    void routes(Javalin app) {
        app.post("/api/health", this::start);
        app.get("/api/health", ctx -> Json.send(ctx, state(job)));
        app.delete("/api/health", ctx -> {
            stop();
            Json.send(ctx, state(job));
        });
    }

    private void start(Context ctx) {
        Job next;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            JsonObject tree = body.getAsJsonObject("variant");
            if (tree == null) {
                throw new IllegalArgumentException("missing \"variant\"");
            }
            tree.remove("builtIn");
            Variant variant = VariantJson.fromTree(tree);
            VariantStore.check(variant);
            VariantHealth.Settings settings = settings(body);
            VariantHealth.check(variant, settings);
            next = new Job(variant, settings);
        } catch (RuntimeException e) {
            JsonObject o = new JsonObject();
            o.addProperty("error", String.valueOf(e.getMessage()));
            ctx.status(400);
            Json.send(ctx, o);
            return;
        }
        stop();
        job = next;
        Thread thread = new Thread(() -> {
            try {
                next.report = VariantHealth.run(next.variant, next.settings, n -> next.done = n, () -> next.cancelled);
            } catch (RuntimeException e) {
                next.error = String.valueOf(e.getMessage());
            }
        }, "variant-health-job");
        thread.setDaemon(true);
        thread.start();
        Json.send(ctx, state(next));
    }

    /** The settings in {@code body}; each one left out keeps its usual value. */
    static VariantHealth.Settings settings(JsonObject body) {
        VariantHealth.Settings d = VariantHealth.Settings.of(40, 2);
        int depth = integer(body, "depth", d.depth());
        String engine = body.has("engine") ? body.get("engine").getAsString() : "built-in";
        return new VariantHealth.Settings(integer(body, "games", d.games()), integer(body, "whiteDepth", depth),
                integer(body, "blackDepth", depth), integer(body, "randomPlies", d.randomPlies()),
                integer(body, "maxPlies", d.maxPlies()),
                body.has("seed") ? body.get("seed").getAsLong() : d.seed(), integer(body, "threads", d.threads()),
                integer(body, "variety", d.variety()), switch (engine) {
                    case "built-in", "BUILT_IN" -> VariantHealth.Engine.BUILT_IN;
                    case "fairy-stockfish", "FAIRY_STOCKFISH" -> VariantHealth.Engine.FAIRY_STOCKFISH;
                    default -> throw new IllegalArgumentException("engine is built-in or fairy-stockfish: " + engine);
                }, body.has("fairyNodes") ? body.get("fairyNodes").getAsLong() : d.fairyNodes());
    }

    private static int integer(JsonObject body, String key, int otherwise) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : otherwise;
    }

    /** Stops the running check, if any. */
    void stop() {
        Job j = job;
        if (j != null && j.running()) {
            j.cancelled = true;
        }
    }

    private static JsonObject state(Job j) {
        JsonObject o = new JsonObject();
        o.addProperty("fairyInstalled", FAIRY_INSTALLED);
        o.addProperty("cores", Runtime.getRuntime().availableProcessors());
        if (j == null) {
            o.addProperty("running", false);
            return o;
        }
        o.addProperty("running", j.running());
        o.addProperty("cancelled", j.cancelled);
        o.addProperty("variantId", j.variant.id());
        o.addProperty("variantName", j.variant.name());
        o.addProperty("games", j.settings.games());
        o.addProperty("depth", j.settings.depth());
        o.addProperty("done", j.done);
        VariantHealth.Settings s = j.settings;
        JsonObject settings = new JsonObject();
        settings.addProperty("games", s.games());
        settings.addProperty("whiteDepth", s.whiteDepth());
        settings.addProperty("blackDepth", s.blackDepth());
        settings.addProperty("randomPlies", s.randomPlies());
        settings.addProperty("maxPlies", s.maxPlies());
        settings.addProperty("seed", s.seed());
        settings.addProperty("threads", s.threadCount());
        settings.addProperty("variety", s.variety());
        settings.addProperty("engine", s.engine() == VariantHealth.Engine.BUILT_IN ? "built-in" : "fairy-stockfish");
        settings.addProperty("fairyNodes", s.fairyNodes());
        o.add("settings", settings);
        if (j.error != null) {
            o.addProperty("error", j.error);
        }
        VariantHealth.Report r = j.report;
        if (r != null) {
            JsonObject report = new JsonObject();
            report.addProperty("games", r.games());
            report.addProperty("whiteWins", r.whiteWins());
            report.addProperty("blackWins", r.blackWins());
            report.addProperty("draws", r.draws());
            report.addProperty("averagePlies", r.averagePlies());
            report.addProperty("shortest", r.shortest());
            report.addProperty("longest", r.longest());
            report.addProperty("movesPerTurn", r.movesPerTurn());
            report.addProperty("whiteScore", r.whiteScore());
            report.addProperty("decisiveShare", r.decisiveShare());
            JsonObject endings = new JsonObject();
            for (Map.Entry<String, Integer> e : r.endings().entrySet()) {
                endings.addProperty(e.getKey(), e.getValue());
            }
            report.add("endings", endings);
            JsonArray notes = new JsonArray();
            r.notes().forEach(notes::add);
            report.add("notes", notes);
            report.add("details", tree(r.details()));
            o.add("report", report);
        }
        return o;
    }

    /** A record (and the lists, maps, strings and numbers in it) as JSON; Gson 2.8 does not know records. */
    static JsonElement tree(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Record record) {
            JsonObject o = new JsonObject();
            for (RecordComponent c : record.getClass().getRecordComponents()) {
                try {
                    o.add(c.getName(), tree(c.getAccessor().invoke(record)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            return o;
        }
        if (value instanceof List<?> list) {
            JsonArray a = new JsonArray();
            list.forEach(item -> a.add(tree(item)));
            return a;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject o = new JsonObject();
            map.forEach((k, v) -> o.add(String.valueOf(k), tree(v)));
            return o;
        }
        if (value instanceof Double d) {
            return new JsonPrimitive(Double.isFinite(d) ? Math.round(d * 10_000) / 10_000.0 : 0);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        return new JsonPrimitive(String.valueOf(value));
    }

}
