package web;

import analysis.GameAnalyzer;
import analysis.UciEvaluator;
import analysis.UciEvaluator.Score;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import engine.StockfishLocator;
import io.javalin.Javalin;
import io.javalin.http.Context;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The live evaluation bar: the score of one position, asked after every move. Stockfish at full
 * strength judges it to the analysis's depth ({@link GameAnalyzer#DEFAULT_DEPTH}), so the bar does
 * not jump when a full analysis comes in. The engine is its own process, started on the first
 * request and kept, apart from the opponent and the analysis, so none of them waits on another.
 *
 * <pre>
 * POST /api/eval {startFen?, moves: [uci...], variant?} → {score: {cp} | {mate}} (503 if there is no
 * Stockfish, 422 for a variant other than chess)
 * </pre>
 */
final class EvalApi {

    static final int DEPTH = GameAnalyzer.DEFAULT_DEPTH;

    private final Supplier<List<String>> command;
    /** The engine and the command it was started with; touched only under the lock. */
    private UciEvaluator evaluator;
    private List<String> started;

    /** Stockfish wherever {@link StockfishLocator} finds it, looked up again on each request. */
    EvalApi() {
        this(() -> StockfishLocator.find().map(p -> List.of(p.toString())).orElse(null));
    }

    /** @param command the engine to run (null from the supplier: none installed) */
    EvalApi(Supplier<List<String>> command) {
        this.command = command;
    }

    void routes(Javalin app) {
        app.post("/api/eval", this::eval);
    }

    synchronized void shutdown() {
        if (evaluator != null) {
            evaluator.close();
            evaluator = null;
        }
    }

    private void eval(Context ctx) {
        if (!chessOnly(ctx)) {
            return;
        }
        String startFen;
        List<String> moves = new ArrayList<>();
        String fen;
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            JsonElement start = body.get("startFen");
            startFen = start == null || start.isJsonNull() ? Position.START_FEN : start.getAsString();
            fen = startFen;
            for (JsonElement m : body.getAsJsonArray("moves")) {
                ChessMove move = ChessMove.fromUci(m.getAsString());
                if (!Rules.isLegal(fen, move)) {
                    throw new IllegalArgumentException("illegal move");
                }
                fen = Rules.applyMove(fen, move);
                moves.add(m.getAsString());
            }
        } catch (RuntimeException e) {
            ctx.status(400);
            json(ctx, error("expected {startFen?, moves: [uci...]} of legal moves"));
            return;
        }
        Score over = GameAnalyzer.gameOverScore(fen);
        if (over != null) {
            json(ctx, result(over));
            return;
        }
        List<String> engine = command.get();
        if (engine == null) {
            ctx.status(503);
            JsonObject o = error("The evaluation bar needs Stockfish, and it is not installed yet.");
            o.addProperty("noStockfish", true);
            json(ctx, o);
            return;
        }
        try {
            json(ctx, result(evaluate(engine, startFen, moves, fen.split(" ")[1].equals("w"))));
        } catch (IOException | RuntimeException e) {
            ctx.status(500);
            json(ctx, error(String.valueOf(e.getMessage())));
        }
    }

    /**
     * Stockfish judges chess only: a request naming another variant ({@code variant}, an id from
     * ai.variant.Variants) is answered 422 {error, unsupportedVariant: true}. Returns false then.
     */
    static boolean chessOnly(Context ctx) {
        String variant;
        try {
            JsonElement v = JsonParser.parseString(ctx.body()).getAsJsonObject().get("variant");
            variant = v == null || v.isJsonNull() ? "chess" : v.getAsString();
        } catch (RuntimeException e) {
            return true; // malformed: the request's own parsing answers it
        }
        if (variant.equals("chess")) {
            return true;
        }
        ctx.status(422);
        JsonObject o = new JsonObject();
        o.addProperty("error", "Stockfish judges chess only, not " + variant + ".");
        o.addProperty("unsupportedVariant", true);
        ctx.contentType("application/json").result(o.toString());
        return false;
    }

    private synchronized Score evaluate(List<String> engine, String startFen, List<String> moves, boolean whiteToMove)
            throws IOException {
        if (evaluator == null || !engine.equals(started)) {
            shutdown();
            evaluator = new UciEvaluator(engine);
            started = engine;
        }
        try {
            return evaluator.evaluate(startFen, moves, DEPTH, whiteToMove).score();
        } catch (IOException e) {
            shutdown(); // a hung or dead engine is started afresh next time
            throw e;
        }
    }

    private static JsonObject result(Score s) {
        JsonObject score = new JsonObject();
        if (s.mate() != null) {
            score.addProperty("mate", s.mate());
        } else {
            score.addProperty("cp", s.cp());
        }
        JsonObject o = new JsonObject();
        o.add("score", score);
        return o;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static void json(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
