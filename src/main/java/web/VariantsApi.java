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

import java.util.Optional;

/**
 * The variants, built in and made by the player (Phase 6 R5a), for the variant designer:
 * <ul>
 *   <li>{@code GET /api/variants}: {folder, variants: [{id, name, builtIn, fairy, fairyReason, goals,
 *       checksToWin, family, notes, pieces, width, height, start, modified}...]}, built-ins first;
 *       {@code goals} as in {@code ai.variant.VariantJson}, {@code fairy} whether Fairy-Stockfish plays it
 *       ({@code fairyReason} why not, else null), {@code pieces} is how many piece types, {@code modified}
 *       when a made variant last changed (epoch millis, 0 for a built-in)</li>
 *   <li>{@code POST /api/variant-rules} {the whole variant}: what its rules come to without saving it,
 *       for the designer: {error} when it cannot be played (else null), {fairy, fairyReason}, and
 *       {castlings: [{side, kingSide, king, kingTo, rook, rookTo}...]} as the engine reads them</li>
 *   <li>{@code GET /api/variants/{id}}: the whole variant ({@code ai.variant.VariantJson}) plus builtIn,
 *       family and notes</li>
 *   <li>{@code PUT /api/variants/{id}}: saves a made variant (the body is the whole variant, its id
 *       the path's, plus optional family and notes, kept beside it by {@link VariantStore#saveAbout});
 *       400 {error} says why one cannot be played</li>
 *   <li>{@code DELETE /api/variants/{id}}: removes a made variant and its notes (games played with it
 *       keep their copy)</li>
 *   <li>{@code POST /api/betza} {text} → {atoms}, or {atoms} → {text}; 400 {error} for text that is not Betza</li>
 *   <li>{@code GET/PUT/DELETE /api/variants/{id}/art/{letter}/{side}}: a made piece's picture for
 *       white ({@code w}) or black ({@code b}); PUT takes the image as the body with its
 *       Content-Type (PNG, JPEG, WebP, GIF or SVG, at most 1 MB). {@code GET /api/variants/{id}}
 *       lists them as {@code art: {letter: {side: savedMillis}}}</li>
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
                o.addProperty("fairy", arena.FairyStockfish.plays(v)); // Fairy-Stockfish can be its yardstick
                o.addProperty("fairyReason", arena.FairyConfig.refusal(v).orElse(null));
                o.add("goals", VariantJson.goals(v.goals()));
                o.addProperty("checksToWin", v.checksToWin());
                VariantStore.About about = store.about(v.id());
                o.addProperty("family", about.family());
                o.addProperty("notes", about.notes());
                o.addProperty("pieces", v.pieces().size());
                o.addProperty("width", v.grid().width());
                o.addProperty("height", v.grid().height());
                o.addProperty("start", v.startFen());
                o.addProperty("modified", store.modified(v.id()));
                list.add(o);
            }
            out.add("variants", list);
            Json.send(ctx, out);
        });
        app.get("/api/variants/{id}", ctx -> {
            Variant v = store.byId(ctx.pathParam("id"))
                    .orElseThrow(() -> new NotFoundResponse("no variant " + ctx.pathParam("id")));
            JsonObject o = VariantJson.toTree(v);
            o.addProperty("builtIn", VariantStore.isBuiltIn(v.id()));
            addAbout(o, store.about(v.id()));
            o.add("art", artIndex(v.id()));
            Json.send(ctx, o);
        });
        app.get("/api/variants/{id}/art/{letter}/{side}", ctx -> {
            VariantStore.Art art;
            try {
                art = artKey(ctx, (id, letter, side) -> store.art(id, letter, side)).orElse(null);
            } catch (IllegalArgumentException e) {
                art = null;
            }
            if (art == null) {
                throw new NotFoundResponse("no picture");
            }
            ctx.header("Cache-Control", "no-cache");
            // an uploaded SVG is shown as an image only, never run as a page
            ctx.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.contentType(art.type()).result(art.bytes());
        });
        app.put("/api/variants/{id}/art/{letter}/{side}", ctx -> {
            try {
                String type = String.valueOf(ctx.contentType()).split(";")[0].trim();
                artKey(ctx, (id, letter, side) -> {
                    store.saveArt(id, letter, side, type, ctx.bodyAsBytes());
                    return Optional.empty();
                });
            } catch (IllegalArgumentException e) {
                error(ctx, 400, e.getMessage());
                return;
            }
            Json.send(ctx, artIndex(ctx.pathParam("id")));
        });
        app.delete("/api/variants/{id}/art/{letter}/{side}", ctx -> {
            try {
                artKey(ctx, (id, letter, side) -> {
                    store.deleteArt(id, letter, side);
                    return Optional.empty();
                });
            } catch (IllegalArgumentException e) {
                error(ctx, 400, e.getMessage());
                return;
            }
            Json.send(ctx, artIndex(ctx.pathParam("id")));
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
        app.post("/api/variant-rules", this::rules);
    }

    /** What an edited variant's rules come to: playable or why not, Fairy-Stockfish, the castlings. */
    private void rules(Context ctx) {
        JsonObject out = new JsonObject();
        Variant v;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            body.remove("builtIn");
            body.remove("art");
            body.remove("family");
            body.remove("notes");
            v = VariantJson.fromTree(body);
        } catch (RuntimeException e) {
            error(ctx, 400, String.valueOf(e.getMessage()));
            return;
        }
        try {
            VariantStore.check(v);
            out.add("error", null);
        } catch (RuntimeException e) {
            out.addProperty("error", e.getMessage());
        }
        out.addProperty("fairy", arena.FairyStockfish.plays(v));
        out.addProperty("fairyReason", arena.FairyConfig.refusal(v).orElse(null));
        JsonArray castlings = new JsonArray();
        try {
            for (ai.board.BoardRules.Castling c : ai.board.BoardRules.of(v).castlings()) {
                JsonObject o = new JsonObject();
                o.addProperty("side", c.player() == 0 ? "white" : "black");
                o.addProperty("kingSide", c.kingSide());
                o.addProperty("king", square(v, c.kingFrom()));
                o.addProperty("kingTo", square(v, c.kingTo()));
                o.addProperty("rook", square(v, c.rookFrom()));
                o.addProperty("rookTo", square(v, c.rookTo()));
                castlings.add(o);
            }
        } catch (RuntimeException e) {
            // a start position that does not read: no castlings (the error says why)
        }
        out.add("castlings", castlings);
        Json.send(ctx, out);
    }

    private static String square(Variant v, int sq) {
        return v.grid().name(sq);
    }

    private interface ArtAction {
        Optional<VariantStore.Art> run(String id, char letter, char side);
    }

    /** Runs {@code action} on the path's variant, letter and side; a malformed one is a 400. */
    private static Optional<VariantStore.Art> artKey(Context ctx, ArtAction action) {
        String letter = ctx.pathParam("letter");
        String side = ctx.pathParam("side");
        if (letter.length() != 1 || side.length() != 1) {
            throw new IllegalArgumentException("a picture is for one letter and side w or b");
        }
        return action.run(ctx.pathParam("id"), letter.charAt(0), side.charAt(0));
    }

    private JsonObject artIndex(String id) {
        JsonObject art = new JsonObject();
        store.artIndex(id).forEach((letter, sides) -> {
            JsonObject o = new JsonObject();
            sides.forEach((side, millis) -> o.addProperty(String.valueOf(side), millis));
            art.add(String.valueOf(letter), o);
        });
        return art;
    }

    private void save(Context ctx) {
        Variant v;
        VariantStore.About about;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            body.remove("builtIn");
            body.remove("art");
            about = new VariantStore.About(text(body.remove("family")), text(body.remove("notes")));
            v = VariantJson.fromTree(body);
            if (!v.id().equals(ctx.pathParam("id"))) {
                throw new IllegalArgumentException("the variant's id is \"" + v.id() + "\", not \"" + ctx.pathParam("id") + "\"");
            }
            store.save(v);
            store.saveAbout(v.id(), about);
        } catch (IllegalArgumentException e) {
            error(ctx, 400, e.getMessage());
            return;
        } catch (RuntimeException e) {
            error(ctx, 400, "not a variant: " + e.getMessage());
            return;
        }
        JsonObject o = VariantJson.toTree(v);
        o.addProperty("builtIn", false);
        addAbout(o, about);
        o.add("art", artIndex(v.id()));
        Json.send(ctx, o);
    }

    private static String text(JsonElement e) {
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static void addAbout(JsonObject o, VariantStore.About about) {
        o.addProperty("family", about.family());
        o.addProperty("notes", about.notes());
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
            Json.send(ctx, out);
        } catch (RuntimeException e) {
            error(ctx, 400, String.valueOf(e.getMessage()));
        }
    }

    private static void error(Context ctx, int status, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        ctx.status(status);
        Json.send(ctx, o);
    }

}
