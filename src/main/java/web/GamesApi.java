package web;

import ai.variant.Variants;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import game.GameArchive;
import game.GameConfig;
import game.SavedGame;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import rules.Game;

/**
 * The "My games" page's API over the saved games ({@link GameArchive}; {@link GameHub} saves them):
 * <ul>
 *   <li>{@code GET /api/games}: {folder, games: [summary...]}, newest first</li>
 *   <li>{@code GET /api/games/{id}}: one game in full: its summary plus startFen, moves (as the
 *       game screen gets them: uci, san, fenAfter...), clock {white, black} (time left, or null)
 *       and pgn</li>
 *   <li>{@code DELETE /api/games/{id}}</li>
 * </ul>
 * A summary is {id, started, updated, mode: engine|friend|computer (computer: the engine played
 * itself), humanColor, level (in computer mode White's), blackLevel (Black's in computer mode, else
 * the same as level), opponent (a champion's name, or null), weights (tuned|classic), variant (an id: a built-in from ai.variant.Variants or one the player made; chess for games saved before variants), variantName, time {initialMs, incrementMs} or null, plies, result (null while
 * unfinished), termination}. Carrying a game on goes over the game's WebSocket ({@code resumeGame}).
 */
final class GamesApi {

    private final GameArchive archive;

    GamesApi(GameArchive archive) {
        this.archive = archive;
    }

    void routes(Javalin app) {
        app.get("/api/games", ctx -> {
            JsonObject out = new JsonObject();
            out.addProperty("folder", archive.dir().toAbsolutePath().normalize().toString());
            JsonArray games = new JsonArray();
            archive.list().forEach(g -> games.add(summary(g)));
            out.add("games", games);
            Json.send(ctx, out);
        });
        app.get("/api/games/{id}", ctx -> Json.send(ctx, detail(find(ctx))));
        app.delete("/api/games/{id}", ctx -> {
            if (!archive.delete(ctx.pathParam("id"))) {
                throw new NotFoundResponse("no saved game " + ctx.pathParam("id"));
            }
            ctx.status(204);
        });
    }

    private SavedGame find(Context ctx) {
        return archive.get(ctx.pathParam("id")).orElseThrow(() -> new NotFoundResponse("no saved game " + ctx.pathParam("id")));
    }

    static JsonObject summary(SavedGame g) {
        JsonObject o = new JsonObject();
        o.addProperty("id", g.id());
        o.addProperty("started", g.started().toString());
        o.addProperty("updated", g.updated().toString());
        GameConfig config = g.config();
        JsonObject c = GameStateJson.config(config);
        o.addProperty("mode", c.get("mode").getAsString());
        o.addProperty("humanColor", c.get("humanColor").getAsString());
        o.addProperty("level", config.skillLevel());
        o.addProperty("blackLevel", config.skillLevelFor(false));
        o.addProperty("opponent", g.opponentLabel());
        o.addProperty("weights", g.weights());
        o.addProperty("variant", g.variant());
        o.addProperty("variantName", variantName(g));
        if (g.timeControl().isTimed()) {
            JsonObject time = new JsonObject();
            time.addProperty("initialMs", g.timeControl().initialMs());
            time.addProperty("incrementMs", g.timeControl().incrementMs());
            o.add("time", time);
        } else {
            o.add("time", null);
        }
        o.addProperty("plies", g.moves().size());
        o.addProperty("result", g.result());
        o.addProperty("termination", g.termination());
        return o;
    }

    /** The variant's name as the game knew it (a made variant's from its copy in the game). */
    private static String variantName(SavedGame g) {
        try {
            if (g.variantDef() != null) {
                return ai.variant.VariantJson.read(g.variantDef()).name();
            }
        } catch (RuntimeException e) {
            return g.variant();
        }
        return Variants.byId(g.variant()).map(ai.variant.Variant::name).orElse(g.variant());
    }

    private static JsonObject detail(SavedGame g) {
        JsonObject o = summary(g);
        o.addProperty("startFen", g.startFen());
        Game game = new Game(g.variantDef() != null ? ai.variant.VariantJson.read(g.variantDef()) : Variants.byId(g.variant())
                .orElseThrow(() -> new IllegalArgumentException("unknown variant " + g.variant())), g.startFen());
        JsonArray moves = new JsonArray();
        g.moves().forEach(uci -> moves.add(GameStateJson.move(game.play(uci))));
        o.add("moves", moves);
        if (g.whiteMs() != null && g.blackMs() != null) {
            JsonObject clock = new JsonObject();
            clock.addProperty("white", g.whiteMs());
            clock.addProperty("black", g.blackMs());
            o.add("clock", clock);
        } else {
            o.add("clock", null);
        }
        o.addProperty("pgn", g.pgn());
        return o;
    }

}
