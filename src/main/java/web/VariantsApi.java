package web;

import ai.piece.Betza;
import ai.variant.Variant;
import ai.variant.VariantJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import game.VariantStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

/**
 * The variants, built in and made by the player (Phase 6 R5a), for the variant designer:
 * <ul>
 *   <li>{@code GET /api/variants}: {folder, variants: [{id, name, builtIn, goal}...]}, built-ins first</li>
 *   <li>{@code GET /api/variants/{id}}: the whole variant ({@code ai.variant.VariantJson}) plus builtIn</li>
 *   <li>{@code PUT /api/variants/{id}}: saves a made variant (the body is the whole variant, its id
 *       the path's); 400 {error} says why one cannot be played</li>
 *   <li>{@code DELETE /api/variants/{id}}: removes a made variant (games played with it keep their copy)</li>
 *   <li>{@code POST /api/betza} {text} → {atoms}, or {atoms} → {text}; 400 {error} for text that is not Betza</li>
 * </ul>
 */
final class VariantsApi {

    private final VariantStore store;

    VariantsApi(VariantStore store) {
        this.store = store;
    }

    void routes(Javalin app) {
        app.get("/api/variants", ctx -> {
            JsonObject out = new JsonObject();
            out.addProperty("folder", store.dir().toAbsolutePath().normalize().toString());
            JsonArray list = new JsonArray();
            for (Variant v : store.all()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", v.id());
                o.addProperty("name", v.name());
                o.addProperty("builtIn", VariantStore.isBuiltIn(v.id()));
                o.addProperty("goal", v.goal().name());
                list.add(o);
            }
            out.add("variants", list);
            json(ctx, out);
        });
        app.get("/api/variants/{id}", ctx -> {
            Variant v = store.byId(ctx.pathParam("id"))
                    .orElseThrow(() -> new NotFoundResponse("no variant " + ctx.pathParam("id")));
            JsonObject o = VariantJson.toTree(v);
            o.addProperty("builtIn", VariantStore.isBuiltIn(v.id()));
            json(ctx, o);
        });
        app.put("/api/variants/{id}", this::save);
        app.delete("/api/variants/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (VariantStore.isBuiltIn(id)) {
                error(ctx, 400, "a built-in variant cannot be deleted");
                return;
            }
            if (!store.delete(id)) {
                throw new NotFoundResponse("no variant " + id);
            }
            ctx.status(204);
        });
        app.post("/api/betza", this::betza);
    }

    private void save(Context ctx) {
        Variant v;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            body.remove("builtIn");
            v = VariantJson.fromTree(body);
            if (!v.id().equals(ctx.pathParam("id"))) {
                throw new IllegalArgumentException("the variant's id is \"" + v.id() + "\", not \"" + ctx.pathParam("id") + "\"");
            }
            store.save(v);
        } catch (IllegalArgumentException e) {
            error(ctx, 400, e.getMessage());
            return;
        } catch (RuntimeException e) {
            error(ctx, 400, "not a variant: " + e.getMessage());
            return;
        }
        JsonObject o = VariantJson.toTree(v);
        o.addProperty("builtIn", false);
        json(ctx, o);
    }

    private void betza(Context ctx) {
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            JsonObject out = new JsonObject();
            JsonElement text = body.get("text");
            if (text != null && !text.isJsonNull()) {
                out.add("atoms", VariantJson.atoms(Betza.parse(text.getAsString())));
            } else {
                out.addProperty("text", Betza.write(VariantJson.atoms(body.getAsJsonArray("atoms"))));
            }
            json(ctx, out);
        } catch (RuntimeException e) {
            error(ctx, 400, String.valueOf(e.getMessage()));
        }
    }

    private static void error(Context ctx, int status, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        ctx.status(status);
        json(ctx, o);
    }

    private static void json(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
