package web;

import ai.variant.Variant;
import ai.variant.VariantJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import game.VariantStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lab.VariantHealth;

import java.util.Map;

/**
 * The variant health check over HTTP (Phase 6 R5d), for the designer. One check runs at a time; a
 * new one stops the last.
 * <ul>
 *   <li>{@code POST /api/health} {variant (the whole variant, saved or not), games, depth}: starts a
 *       check; 400 {error} when the variant cannot be played</li>
 *   <li>{@code GET /api/health}: {running, variantId, variantName, games, depth, done, report?, error?};
 *       {running: false} before any check</li>
 *   <li>{@code DELETE /api/health}: stops the running check</li>
 * </ul>
 */
final class HealthApi {

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
        app.get("/api/health", ctx -> json(ctx, state(job)));
        app.delete("/api/health", ctx -> {
            stop();
            json(ctx, state(job));
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
            int games = body.has("games") ? body.get("games").getAsInt() : 40;
            int depth = body.has("depth") ? body.get("depth").getAsInt() : 2;
            next = new Job(variant, VariantHealth.Settings.of(games, depth));
        } catch (RuntimeException e) {
            JsonObject o = new JsonObject();
            o.addProperty("error", String.valueOf(e.getMessage()));
            ctx.status(400);
            json(ctx, o);
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
        json(ctx, state(next));
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
            com.google.gson.JsonArray notes = new com.google.gson.JsonArray();
            r.notes().forEach(notes::add);
            report.add("notes", notes);
            o.add("report", report);
        }
        return o;
    }

    private static void json(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
