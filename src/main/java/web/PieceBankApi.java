package web;

import ai.piece.PieceType;
import ai.variant.VariantJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import game.PieceBank;
import game.VariantStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

/**
 * The piece bank ({@link PieceBank}): pieces kept apart from any variant, for the variant designer.
 * <ul>
 *   <li>{@code GET /api/piece-bank}: {pieces: [{id, piece, art}...]} by name; {@code piece} as in
 *       {@code ai.variant.VariantJson}, {@code art} {side: savedMillis}</li>
 *   <li>{@code POST /api/piece-bank} {piece}: adds it under an id made from its name; answers the entry</li>
 *   <li>{@code PUT /api/piece-bank/{id}} {piece}: replaces it (its pictures stay); answers the entry</li>
 *   <li>{@code DELETE /api/piece-bank/{id}}: removes it and its pictures</li>
 *   <li>{@code GET/PUT/DELETE /api/piece-bank/{id}/art/{side}}: its picture for white ({@code w}) or
 *       black ({@code b}), as for a variant's piece; PUT and DELETE answer the entry</li>
 * </ul>
 * A 400 {error} says why a piece or picture was refused.
 */
final class PieceBankApi {

    private final PieceBank bank;

    PieceBankApi(PieceBank bank) {
        this.bank = bank;
    }

    void routes(Javalin app) {
        app.get("/api/piece-bank", ctx -> {
            JsonArray list = new JsonArray();
            for (PieceBank.Entry e : bank.all()) {
                list.add(entry(e));
            }
            JsonObject out = new JsonObject();
            out.add("pieces", list);
            Json.send(ctx, out);
        });
        app.post("/api/piece-bank", ctx -> {
            PieceType piece = piece(ctx);
            if (piece != null) {
                send(ctx, bank.add(piece));
            }
        });
        app.put("/api/piece-bank/{id}", ctx -> {
            PieceType piece = piece(ctx);
            if (piece == null) {
                return;
            }
            try {
                bank.put(ctx.pathParam("id"), piece);
            } catch (IllegalArgumentException e) {
                error(ctx, e.getMessage());
                return;
            }
            send(ctx, ctx.pathParam("id"));
        });
        app.delete("/api/piece-bank/{id}", ctx -> {
            if (!bank.delete(ctx.pathParam("id"))) {
                throw new NotFoundResponse("no piece " + ctx.pathParam("id"));
            }
            ctx.status(204);
        });
        app.get("/api/piece-bank/{id}/art/{side}", ctx -> {
            String side = ctx.pathParam("side");
            VariantStore.Art art = side.length() == 1 ? bank.art(ctx.pathParam("id"), side.charAt(0)).orElse(null) : null;
            if (art == null) {
                throw new NotFoundResponse("no picture");
            }
            ctx.header("Cache-Control", "no-cache");
            // an uploaded SVG is shown as an image only, never run as a page
            ctx.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.contentType(art.type()).result(art.bytes());
        });
        app.put("/api/piece-bank/{id}/art/{side}", ctx -> {
            try {
                String type = String.valueOf(ctx.contentType()).split(";")[0].trim();
                bank.saveArt(ctx.pathParam("id"), side(ctx), type, ctx.bodyAsBytes());
            } catch (IllegalArgumentException e) {
                error(ctx, e.getMessage());
                return;
            }
            send(ctx, ctx.pathParam("id"));
        });
        app.delete("/api/piece-bank/{id}/art/{side}", ctx -> {
            try {
                bank.deleteArt(ctx.pathParam("id"), side(ctx));
            } catch (IllegalArgumentException e) {
                error(ctx, e.getMessage());
                return;
            }
            send(ctx, ctx.pathParam("id"));
        });
    }

    private static char side(Context ctx) {
        String side = ctx.pathParam("side");
        if (side.length() != 1) {
            throw new IllegalArgumentException("a picture is for side w or b");
        }
        return side.charAt(0);
    }

    /** The body's piece, or null after answering 400 with why it does not read. */
    private static PieceType piece(Context ctx) {
        try {
            return VariantJson.piece(JsonParser.parseString(ctx.body()).getAsJsonObject());
        } catch (RuntimeException e) {
            error(ctx, "not a piece: " + e.getMessage());
            return null;
        }
    }

    private void send(Context ctx, String id) {
        Json.send(ctx, entry(bank.byId(id).orElseThrow(() -> new NotFoundResponse("no piece " + id))));
    }

    private static JsonObject entry(PieceBank.Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id());
        o.add("piece", VariantJson.piece(e.piece()));
        JsonObject art = new JsonObject();
        e.art().forEach((side, millis) -> art.addProperty(String.valueOf(side), millis));
        o.add("art", art);
        return o;
    }

    private static void error(Context ctx, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        ctx.status(400);
        Json.send(ctx, o);
    }
}
