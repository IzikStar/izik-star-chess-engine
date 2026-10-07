package web;

import ai.eval.ParamSchema;
import ai.eval.ParamSpec;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.Score;
import evolution.Generation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import lab.HallOfFame;
import lab.RunPgn;
import lab.RunStore;
import rules.Game;
import rules.MoveResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The lab page's read-only HTTP API over the evolution runs in a folder ({@code runs/} by default,
 * one SQLite file each, written by {@code lab.Cli}). A run may still be going: SQLite lets the
 * page read while the runner writes.
 *
 * <pre>
 * GET /api/lab/runs                                  the runs, newest first
 * GET /api/lab/runs/{file}                           one run: settings, generations, how the weights moved
 * GET /api/lab/runs/{file}/generations/{n}           a generation's games
 * GET /api/lab/runs/{file}/generations/{n}/games/{i} one game, move by move, for replay
 * GET /api/lab/runs/{file}/pgn                       every game of the run, PGN (a download)
 * POST /api/lab/runs/{file}/generations/{n}/keep     {member, name?, note?}: into the hall of fame
 * GET /api/lab/runs/{file}/schema                    every parameter the run's weights have
 * GET /api/lab/runs/{file}/generations/{n}/members/{i} a member's weights, a parameter file (a download)
 * GET /api/lab/fame                                  the hall of fame, newest first
 * GET /api/lab/fame/{name}/pgn                       an entry's games, PGN (a download)
 * </pre>
 */
final class LabApi {

    private static final Pattern RUN_FILE = Pattern.compile("[A-Za-z0-9._-]+\\.db");
    /** The weight table shows at most this many parameters, those that moved most first. */
    static final int MAX_WEIGHTS = 60;

    private final Path dir;

    LabApi(Path dir) {
        this.dir = dir;
    }

    void routes(Javalin app) {
        app.get("/api/lab/runs", ctx -> Json.send(ctx, runs()));
        app.get("/api/lab/runs/{file}", ctx -> Json.send(ctx, run(ctx.pathParam("file"))));
        app.get("/api/lab/runs/{file}/generations/{n}", ctx ->
                Json.send(ctx, generation(ctx.pathParam("file"), Integer.parseInt(ctx.pathParam("n")))));
        app.get("/api/lab/runs/{file}/generations/{n}/games/{i}", ctx -> Json.send(ctx, game(ctx.pathParam("file"),
                Integer.parseInt(ctx.pathParam("n")), Integer.parseInt(ctx.pathParam("i")))));
        app.get("/api/lab/runs/{file}/pgn", ctx -> download(ctx, ctx.pathParam("file").replaceFirst("\\.db$", ".pgn"),
                runPgn(ctx.pathParam("file"))));
        app.post("/api/lab/runs/{file}/generations/{n}/keep", ctx -> Json.send(ctx, keep(ctx.pathParam("file"),
                Integer.parseInt(ctx.pathParam("n")), JsonParser.parseString(ctx.body()).getAsJsonObject())));
        app.get("/api/lab/runs/{file}/schema", ctx -> Json.send(ctx, schemaOf(ctx.pathParam("file"))));
        app.get("/api/lab/runs/{file}/generations/{n}/members/{i}", ctx -> ctx.header("Content-Disposition",
                        "attachment; filename=\"" + ctx.pathParam("file").replaceFirst("\\.db$", "") + "-g" + ctx.pathParam("n")
                                + "-m" + ctx.pathParam("i") + ".json\"").contentType("application/json")
                .result(memberJson(ctx.pathParam("file"), Integer.parseInt(ctx.pathParam("n")), Integer.parseInt(ctx.pathParam("i")))));
        app.get("/api/lab/fame", ctx -> Json.send(ctx, fame()));
        app.get("/api/lab/fame/{name}/pgn", ctx -> download(ctx, ctx.pathParam("name") + ".pgn",
                hall().get(ctx.pathParam("name")).orElseThrow(() -> new NotFoundResponse("no such entry")).pgn()));
    }

    private static void download(Context ctx, String fileName, String text) {
        ctx.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .contentType("application/x-chess-pgn").result(text);
    }

    // ---- the hall of fame ----------------------------------------------------

    HallOfFame hall() {
        return new HallOfFame(dir.resolve(HallOfFame.FOLDER));
    }

    JsonObject fame() {
        JsonArray list = new JsonArray();
        for (HallOfFame.Entry e : hall().list()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name());
            o.addProperty("reason", e.reason());
            o.addProperty("savedAt", e.savedAt());
            o.addProperty("run", e.run());
            o.addProperty("runName", e.runName());
            o.addProperty("generation", e.generation());
            o.addProperty("member", e.member());
            JsonArray y = new JsonArray();
            e.yardsticks().forEach(y::add);
            o.add("yardsticks", y);
            o.addProperty("games", e.pgn() == null ? 0 : e.pgn().split("\\[Event ", -1).length - 1);
            list.add(o);
        }
        JsonObject out = new JsonObject();
        out.add("entries", list);
        return out;
    }

    JsonObject keep(String file, int generation, JsonObject body) {
        try (RunStore store = open(file)) {
            int member = body.get("member").getAsInt();
            String runName = store.run().map(RunStore.RunRow::name).orElse(file);
            String name = body.has("name") && !body.get("name").getAsString().isBlank()
                    ? body.get("name").getAsString().trim() : HallOfFame.nameFor(runName, generation, member);
            String note = body.has("note") && !body.get("note").getAsString().isBlank()
                    ? body.get("note").getAsString().trim() : "kept by hand";
            HallOfFame.Entry entry = HallOfFame.fromRun(store, dir.resolve(file), generation, member, name, note);
            hall().add(entry);
            JsonObject out = new JsonObject();
            out.addProperty("name", entry.name());
            return out;
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    String runPgn(String file) throws IOException {
        try (RunStore store = open(file)) {
            java.io.StringWriter out = new java.io.StringWriter();
            RunPgn.write(store, -1, -1, out);
            return out.toString();
        }
    }


    // ---- runs ----------------------------------------------------------------

    JsonObject runs() throws IOException {
        JsonObject out = new JsonObject();
        out.addProperty("folder", dir.toAbsolutePath().normalize().toString());
        JsonArray list = new JsonArray();
        if (Files.isDirectory(dir)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(p -> RUN_FILE.matcher(p.getFileName().toString()).matches())
                        .sorted(Comparator.comparing(LabApi::modified).reversed())
                        .toList();
            }
            for (Path file : files) {
                try (RunStore store = RunStore.open(file)) {
                    store.run().ifPresent(run -> {
                        JsonObject o = summary(file.getFileName().toString(), run);
                        List<RunStore.GenerationRow> rows = store.generations();
                        o.addProperty("generationsDone", rows.size());
                        rows.reversed().stream().flatMap(r -> r.yardstick().stream()).findFirst()
                                .ifPresent(score -> o.add("lastYardstick", score(score)));
                        list.add(o);
                    });
                }
            }
        }
        out.add("runs", list);
        return out;
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static JsonObject summary(String file, RunStore.RunRow run) {
        JsonObject o = new JsonObject();
        o.addProperty("file", file);
        o.addProperty("name", run.name());
        o.addProperty("algorithm", run.algorithm());
        o.addProperty("startedAt", run.startedAt());
        o.add("settings", JsonParser.parseString(run.settings().toJson()));
        try {
            ai.variant.Variant v = run.settings().variant();
            o.addProperty("variantName", v.name());
            o.addProperty("variantId", v.id());
        } catch (IllegalArgumentException e) {
            o.addProperty("variantName", run.settings().variantId());
            o.addProperty("variantId", run.settings().variantId());
        }
        return o;
    }

    private static JsonObject stats(RunStore.Stats s) {
        JsonObject o = new JsonObject();
        o.addProperty("games", s.games());
        o.addProperty("whiteWins", s.whiteWins());
        o.addProperty("blackWins", s.blackWins());
        o.addProperty("draws", s.draws());
        o.addProperty("averagePlies", s.averagePlies());
        o.addProperty("capped", s.capped());
        return o;
    }

    JsonObject run(String file) {
        try (RunStore store = open(file)) {
            RunStore.RunRow run = store.run().orElseThrow(() -> new NotFoundResponse("no run in " + file));
            JsonObject out = summary(file, run);
            List<RunStore.GenerationRow> rows = store.generations();
            ParamSchema schema = schema(run);
            JsonArray generations = new JsonArray();
            List<ParamVector> champions = new ArrayList<>();
            for (RunStore.GenerationRow row : rows) {
                JsonObject g = new JsonObject();
                g.addProperty("number", row.number());
                g.addProperty("champion", row.champion());
                g.addProperty("championScore", row.championScore());
                g.addProperty("games", row.games());
                g.addProperty("finishedAt", row.finishedAt());
                g.add("stats", stats(store.stats(row.number(), "population")));
                row.yardstick().ifPresent(score -> g.add("yardstick", score(score)));
                JsonArray yardsticks = new JsonArray();
                for (RunStore.YardstickResult y : row.yardsticks()) {
                    JsonObject j = score(y.score());
                    j.addProperty("opponent", y.opponent());
                    j.addProperty("label", y.label());
                    j.addProperty("depth", y.depth());
                    yardsticks.add(j);
                }
                g.add("yardsticks", yardsticks);
                generations.add(g);
                champions.add(store.members(row.number(), schema).get(row.champion()));
            }
            out.add("generations", generations);
            out.add("weights", weights(schema, champions));
            out.addProperty("parameters", schema.size());
            // the members of the generation under way (the next one's are stored when a generation finishes)
            int next = rows.isEmpty() ? 0 : rows.getLast().number() + 1;
            out.addProperty("nextGeneration", next);
            out.addProperty("nextGenerationGames", next < run.settings().generations() ? store.gameCount(next) : 0);
            return out;
        }
    }

    /** The parameters some champion moved away from its default, those that moved most first. */
    static JsonArray weights(ParamSchema schema, List<ParamVector> champions) {
        record Moved(ParamSpec spec, int index, double change) {}
        List<Moved> moved = new ArrayList<>();
        for (int i = 0; i < schema.size(); i++) {
            ParamSpec spec = schema.spec(i);
            double change = 0;
            for (ParamVector c : champions) {
                change = Math.max(change, Math.abs(c.get(i) - spec.defaultValue()) / (double) (spec.max() - spec.min()));
            }
            if (change > 0) {
                moved.add(new Moved(spec, i, change));
            }
        }
        moved.sort(Comparator.comparingDouble(Moved::change).reversed());
        JsonArray out = new JsonArray();
        for (Moved m : moved.subList(0, Math.min(MAX_WEIGHTS, moved.size()))) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m.spec().name());
            o.addProperty("group", m.spec().group());
            o.addProperty("description", m.spec().description());
            o.addProperty("default", m.spec().defaultValue());
            o.addProperty("min", m.spec().min());
            o.addProperty("max", m.spec().max());
            JsonArray values = new JsonArray();
            champions.forEach(c -> values.add(c.get(m.index())));
            o.add("values", values);
            out.add(o);
        }
        return out;
    }

    // ---- games ---------------------------------------------------------------

    /** The kinds of game a generation plays, in the order the Lab lists them. */
    private static final List<String> KINDS = List.of("population", "yardstick", "stockfish");

    /** A generation's games: the population's, the champion's against the yardsticks, then every member's against Stockfish. */
    private static List<GameRecord> games(RunStore store, int generation) {
        List<GameRecord> games = new ArrayList<>();
        KINDS.forEach(kind -> games.addAll(store.games(generation, kind)));
        return games;
    }

    JsonObject generation(String file, int number) {
        try (RunStore store = open(file)) {
            List<String> kinds = new ArrayList<>();
            List<Integer> depths = new ArrayList<>();
            for (String kind : KINDS) {
                List<Integer> d = store.gameDepths(number, kind);
                d.forEach(x -> kinds.add(kind));
                depths.addAll(d);
            }
            List<GameRecord> games = games(store, number);
            ParamSchema schema = schema(store.run().orElseThrow(() -> new NotFoundResponse("no run in " + file)));
            int size = store.members(number, schema).size();
            JsonObject out = new JsonObject();
            out.addProperty("number", number);
            out.addProperty("members", size);
            List<ParamVector> members = store.members(number, schema);
            out.add("standings", standings(new Generation(number, members,
                    store.games(number, "population"), store.games(number, "stockfish"), 0)));
            out.add("members", members(schema, members));
            JsonArray list = new JsonArray();
            for (int i = 0; i < games.size(); i++) {
                GameRecord g = games.get(i);
                JsonObject o = new JsonObject();
                o.addProperty("index", i);
                o.addProperty("kind", kinds.get(i));
                o.addProperty("white", g.white());
                o.addProperty("black", g.black());
                o.addProperty("opening", g.opening());
                o.addProperty("result", g.result().name());
                o.addProperty("reason", g.reason());
                o.addProperty("plies", g.plies());
                if (depths.get(i) > 0) {
                    o.addProperty("depth", depths.get(i));
                }
                list.add(o);
            }
            out.add("games", list);
            return out;
        }
    }

    /**
     * Each member's score in the generation's own games and, if the run played them, against
     * Stockfish ({@code stockfish}: score 0-1, the level as {@code stockfishLevel}), best first.
     */
    static JsonArray standings(Generation generation) {
        JsonArray out = new JsonArray();
        String sf = generation.stockfishGames().isEmpty() ? null
                : generation.stockfishGames().getFirst().white().startsWith("sf")
                ? generation.stockfishGames().getFirst().white() : generation.stockfishGames().getFirst().black();
        for (int i : generation.ranking()) {
            JsonObject o = new JsonObject();
            o.addProperty("member", i);
            o.addProperty("score", generation.score(i));
            o.addProperty("games", generation.gamesPlayed(i));
            double s = generation.stockfishScore(i);
            if (!Double.isNaN(s)) {
                o.addProperty("stockfish", s);
                o.addProperty("stockfishLevel", Integer.parseInt(sf.substring(2)));
            }
            out.add(o);
        }
        return out;
    }

    /** Each member's weights that are not at their default, by name, with the schema's groups. */
    static JsonArray members(ParamSchema schema, List<ParamVector> members) {
        JsonArray out = new JsonArray();
        for (int m = 0; m < members.size(); m++) {
            ParamVector p = members.get(m);
            JsonObject o = new JsonObject();
            o.addProperty("index", m);
            JsonObject values = new JsonObject();
            for (int i = 0; i < schema.size(); i++) {
                if (p.get(i) != schema.spec(i).defaultValue()) {
                    values.addProperty(schema.spec(i).name(), p.get(i));
                }
            }
            o.add("values", values);
            out.add(o);
        }
        return out;
    }

    /** A member's weights as a parameter file (for {@code arena.Cli} or the designer). */
    String memberJson(String file, int number, int member) {
        try (RunStore store = open(file)) {
            List<ParamVector> members = store.members(number, schema(store.run().orElseThrow()));
            if (member < 0 || member >= members.size()) {
                throw new NotFoundResponse("no member " + member + " in generation " + number);
            }
            return members.get(member).toJson();
        }
    }

    /** The schema of a run's weights: every parameter's name, group, default, range and description. */
    JsonObject schemaOf(String file) {
        try (RunStore store = open(file)) {
            ParamSchema schema = schema(store.run().orElseThrow(() -> new NotFoundResponse("no run in " + file)));
            JsonArray list = new JsonArray();
            for (ParamSpec spec : schema.specs()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", spec.name());
                o.addProperty("group", spec.group());
                o.addProperty("default", spec.defaultValue());
                o.addProperty("min", spec.min());
                o.addProperty("max", spec.max());
                o.addProperty("description", spec.description());
                list.add(o);
            }
            JsonObject out = new JsonObject();
            out.add("parameters", list);
            return out;
        }
    }

    JsonObject game(String file, int number, int index) {
        try (RunStore store = open(file)) {
            List<GameRecord> games = games(store, number);
            if (index < 0 || index >= games.size()) {
                throw new NotFoundResponse("no game " + index + " in generation " + number);
            }
            GameRecord record = games.get(index);
            Game game = new Game(store.run().map(r -> r.settings().variant()).orElse(ai.variant.Variants.CHESS));
            JsonArray moves = new JsonArray();
            for (String uci : record.moves()) {
                MoveResult m = game.play(uci);
                JsonObject o = new JsonObject();
                o.addProperty("uci", m.move().toUci());
                o.addProperty("san", m.san());
                o.addProperty("fenAfter", m.fenAfter());
                moves.add(o);
            }
            JsonObject out = new JsonObject();
            out.addProperty("white", record.white());
            out.addProperty("black", record.black());
            out.addProperty("opening", record.opening());
            out.addProperty("result", record.result().name());
            out.addProperty("reason", record.reason());
            out.addProperty("startFen", game.history().getFirst());
            out.add("moves", moves);
            return out;
        }
    }

    // ---- the champion as an opponent -----------------------------------------

    /** A champion to play against: its weights and the game they are for. */
    record Champion(ParamVector params, ai.variant.Variant variant) {}

    /**
     * Generation {@code number}'s champion in run {@code file}, or the hall of fame's entry NAME
     * when {@code file} is "hof:NAME".
     */
    Champion champion(String file, int number) {
        if (file.startsWith("hof:")) {
            HallOfFame.Entry e = hall().get(file.substring(4)).orElseThrow(() ->
                    new IllegalArgumentException("no hall of fame entry " + file.substring(4)));
            return new Champion(e.params(), e.game());
        }
        try (RunStore store = open(file)) {
            RunStore.GenerationRow row = store.generations().stream().filter(r -> r.number() == number).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("generation " + number + " is not finished"));
            RunStore.RunRow run = store.run().orElseThrow();
            return new Champion(store.members(number, schema(run)).get(row.champion()), run.settings().variant());
        }
    }

    /** A run's name, for labels. */
    String name(String file) {
        if (file.startsWith("hof:")) {
            return "the hall of fame: " + file.substring(4);
        }
        try (RunStore store = open(file)) {
            return store.run().map(RunStore.RunRow::name).orElse(file);
        }
    }

    private static ParamSchema schema(RunStore.RunRow run) {
        return ai.eval.Evaluators.schema(run.settings().variant());
    }

    RunStore open(String file) {
        if (!RUN_FILE.matcher(file).matches() || !Files.isRegularFile(dir.resolve(file))) {
            throw new NotFoundResponse("no run file " + file);
        }
        return RunStore.open(dir.resolve(file));
    }

    Path dir() {
        return dir;
    }

    static boolean isRunFile(String name) {
        return RUN_FILE.matcher(name).matches();
    }

    private static JsonObject score(Score s) {
        JsonObject o = new JsonObject();
        o.addProperty("wins", s.wins());
        o.addProperty("draws", s.draws());
        o.addProperty("losses", s.losses());
        o.addProperty("fraction", s.fraction());
        o.addProperty("elo", s.elo());
        o.addProperty("eloLow", s.eloLow());
        o.addProperty("eloHigh", s.eloHigh());
        return o;
    }
}
