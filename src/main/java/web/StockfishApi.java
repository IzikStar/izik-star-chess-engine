package web;

import com.google.gson.JsonObject;
import engine.StockfishEngine;
import engine.StockfishInstaller;
import engine.StockfishLocator;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stockfish's status, and a one-click download for a player who does not have it.
 *
 * <pre>
 * GET  /api/stockfish          {available, path, installing, progress (0-1, or null while unknown), error}
 * POST /api/stockfish/install  starts the download (if not already running); answers like GET
 * </pre>
 *
 * When the download is done the game's Stockfish switches to it at once and every browser gets a
 * fresh state, so Levels 9-13 and game analysis work without a restart.
 */
final class StockfishApi {

    private final Supplier<StockfishEngine> engine;
    private final Runnable changed;
    /** Downloads Stockfish and returns the executable ({@link StockfishInstaller#install}). */
    interface Installer {
        Path install(List<String> assets, StockfishInstaller.Progress progress) throws java.io.IOException;
    }

    private final Installer installer;
    private final Supplier<List<String>> assets;

    private volatile boolean installing;
    private volatile double progress = -1;
    private volatile String error;
    /** Set by a finished download; a fallback for when there is no game engine to ask. */
    private volatile Path installed;

    StockfishApi(Supplier<StockfishEngine> engine, Runnable changed) {
        this(engine, changed, (assets, progress) -> new StockfishInstaller().install(assets, progress),
                StockfishInstaller::assetsForThisComputer);
    }

    StockfishApi(Supplier<StockfishEngine> engine, Runnable changed, Installer installer, Supplier<List<String>> assets) {
        this.engine = engine;
        this.changed = changed;
        this.installer = installer;
        this.assets = assets;
    }

    void routes(Javalin app) {
        app.get("/api/stockfish", ctx -> json(ctx, status()));
        app.post("/api/stockfish/install", this::install);
    }

    private synchronized void install(Context ctx) {
        if (!installing) {
            installing = true;
            progress = -1;
            error = null;
            Thread t = new Thread(this::download, "stockfish-download");
            t.setDaemon(true);
            t.start();
        }
        json(ctx, status());
    }

    private void download() {
        try {
            Path exe = installer.install(assets.get(), (done, total) -> progress = total > 0 ? (double) done / total : -1);
            installed = exe;
            StockfishEngine e = engine.get();
            if (e != null) {
                e.use(exe);
            }
            System.out.println("Stockfish downloaded: " + exe);
        } catch (Exception e) {
            error = "The download failed: " + e.getMessage();
        } finally {
            installing = false;
            changed.run();
        }
    }

    JsonObject status() {
        StockfishEngine e = engine.get();
        String path = e != null ? e.path() : Optional.ofNullable(installed).map(Path::toString)
                .orElseGet(() -> StockfishLocator.find().map(Path::toString).orElse(null));
        JsonObject o = new JsonObject();
        o.addProperty("available", e != null ? e.isAvailable() : path != null);
        o.addProperty("path", path);
        o.addProperty("installing", installing);
        o.addProperty("progress", progress < 0 ? null : Math.round(progress * 1000) / 1000.0);
        o.addProperty("error", error);
        o.addProperty("canInstall", !assets.get().isEmpty());
        return o;
    }

    private static void json(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
