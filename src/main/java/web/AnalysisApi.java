package web;

import analysis.GameAnalyzer;
import analysis.GameAnalyzer.MoveReport;
import analysis.GameAnalyzer.Report;
import analysis.GameAnalyzer.SideReport;
import analysis.UciEvaluator;
import analysis.UciEvaluator.Score;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.StockfishLocator;
import io.javalin.Javalin;
import io.javalin.http.Context;
import rules.Position;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Game analysis over HTTP: the browser sends a game, Stockfish judges every position on a
 * background thread, and the browser polls for progress and then the report
 * ({@link GameAnalyzer}). One analysis runs at a time; a new one waits for the one before.
 *
 * <pre>
 * POST /api/analysis      {startFen?, moves: [uci...], depth?}  → {id} (503 if there is no Stockfish)
 * GET  /api/analysis/{id} → {done, progress, total, error?, report?}
 * </pre>
 */
final class AnalysisApi {

    /** Finished jobs kept for polling; older ones are dropped. */
    private static final int KEEP = 8;

    private final Supplier<List<String>> command;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "analysis");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    private static final class Job {
        final int total;
        volatile int progress;
        volatile JsonObject report;
        volatile String error;

        Job(int total) {
            this.total = total;
        }
    }

    /** Stockfish wherever {@link StockfishLocator} finds it, looked up again on each request. */
    AnalysisApi() {
        this(() -> StockfishLocator.find().map(p -> List.of(p.toString())).orElse(null));
    }

    /** @param command the engine to run (null from the supplier: none installed) */
    AnalysisApi(Supplier<List<String>> command) {
        this.command = command;
    }

    void routes(Javalin app) {
        app.post("/api/analysis", this::start);
        app.get("/api/analysis/{id}", this::poll);
    }

    void shutdown() {
        worker.shutdownNow();
    }

    private void start(Context ctx) {
        List<String> engine = command.get();
        if (engine == null) {
            ctx.status(503);
            JsonObject o = new JsonObject();
            o.addProperty("error", "Analysis needs Stockfish, and it is not installed yet.");
            o.addProperty("noStockfish", true);
            json(ctx, o);
            return;
        }
        String startFen;
        List<String> moves = new ArrayList<>();
        int depth;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            JsonElement fen = body.get("startFen");
            startFen = fen == null || fen.isJsonNull() ? Position.START_FEN : fen.getAsString();
            for (JsonElement m : body.getAsJsonArray("moves")) {
                moves.add(m.getAsString());
            }
            depth = body.has("depth") ? body.get("depth").getAsInt() : GameAnalyzer.DEFAULT_DEPTH;
        } catch (RuntimeException e) {
            ctx.status(400);
            error(ctx, "expected {startFen?, moves: [uci...], depth?}");
            return;
        }
        int d = Math.max(GameAnalyzer.MIN_DEPTH, Math.min(GameAnalyzer.MAX_DEPTH, depth));
        int id = ids.incrementAndGet();
        Job job = new Job(moves.size() + 1);
        jobs.put(id, job);
        jobs.keySet().removeIf(k -> k <= id - KEEP);
        worker.execute(() -> run(job, engine, startFen, moves, d));
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("total", job.total);
        json(ctx, o);
    }

    private static void run(Job job, List<String> engine, String startFen, List<String> moves, int depth) {
        try (UciEvaluator evaluator = new UciEvaluator(engine)) {
            Report report = GameAnalyzer.analyze(evaluator, startFen, moves, depth, done -> job.progress = done);
            job.report = toJson(report);
        } catch (IOException | RuntimeException e) {
            job.error = String.valueOf(e.getMessage());
        }
    }

    private void poll(Context ctx) {
        Job job;
        try {
            job = jobs.get(Integer.parseInt(ctx.pathParam("id")));
        } catch (NumberFormatException e) {
            job = null;
        }
        if (job == null) {
            ctx.status(404);
            error(ctx, "no such analysis");
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("done", job.report != null || job.error != null);
        o.addProperty("progress", job.progress);
        o.addProperty("total", job.total);
        if (job.error != null) {
            o.addProperty("error", job.error);
        }
        if (job.report != null) {
            o.add("report", job.report);
        }
        json(ctx, o);
    }

    static JsonObject toJson(Report r) {
        JsonObject o = new JsonObject();
        o.addProperty("depth", r.depth());
        JsonArray evals = new JsonArray();
        for (Score s : r.evals()) {
            evals.add(score(s));
        }
        o.add("evals", evals);
        JsonArray moves = new JsonArray();
        for (MoveReport m : r.moves()) {
            JsonObject j = new JsonObject();
            j.addProperty("uci", m.uci());
            j.addProperty("san", m.san());
            j.addProperty("color", m.white() ? "white" : "black");
            j.addProperty("bestUci", m.bestUci());
            j.addProperty("bestSan", m.bestSan());
            j.addProperty("cpLoss", m.cpLoss());
            j.addProperty("winChanceLoss", m.winChanceLoss());
            j.addProperty("accuracy", m.accuracy());
            j.addProperty("quality", m.quality().name().toLowerCase());
            moves.add(j);
        }
        o.add("moves", moves);
        o.add("white", side(r.white()));
        o.add("black", side(r.black()));
        return o;
    }

    private static JsonObject score(Score s) {
        JsonObject j = new JsonObject();
        if (s.mate() != null) {
            j.addProperty("mate", s.mate());
        } else {
            j.addProperty("cp", s.cp());
        }
        return j;
    }

    private static JsonObject side(SideReport s) {
        JsonObject j = new JsonObject();
        j.addProperty("moves", s.moves());
        j.addProperty("acpl", s.acpl());
        j.addProperty("accuracy", s.accuracy());
        j.addProperty("elo", s.elo());
        j.addProperty("inaccuracies", s.inaccuracies());
        j.addProperty("mistakes", s.mistakes());
        j.addProperty("blunders", s.blunders());
        return j;
    }

    private static void error(Context ctx, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        json(ctx, o);
    }

    private static void json(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
