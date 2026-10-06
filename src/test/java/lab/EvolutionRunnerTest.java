package lab;

import evolution.Generation;
import evolution.Evolution;
import ai.eval.ChessEvaluate;
import ai.eval.ParamVector;
import arena.GameRecord;
import evolution.RandomMutationExample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.Position;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvolutionRunnerTest {

    /** 3 generations of 4 members at depth 1: 6 pairings × 1 opening × 2 colours = 12 games each. */
    private static final RunSettings TINY = new RunSettings(3, 1, 1, 2, 120, 2, 5, 1, 2);

    private static RandomMutationExample example() {
        return new RandomMutationExample(4, 0.2, 0.05);
    }

    /** Everything a run stored, for comparing two runs. */
    private static List<Object> contents(RunStore store) {
        List<Object> all = new ArrayList<>();
        for (RunStore.GenerationRow row : store.generations()) {
            all.add(row.number() + " " + row.champion() + " " + row.championScore() + " " + row.games() + " " + row.yardstick());
            all.add(store.members(row.number(), ChessEvaluate.SCHEMA));
            all.add(store.games(row.number(), "population"));
            all.add(store.games(row.number(), "yardstick"));
        }
        return all;
    }

    @Test
    @DisplayName("A short run records every generation, member and game")
    void recordsEverything(@TempDir Path dir) {
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "tiny", example(), TINY, EvolutionRunner.Listener.SILENT, () -> false);

            assertEquals("tiny", store.run().orElseThrow().name());
            assertEquals(TINY, store.run().orElseThrow().settings());
            List<RunStore.GenerationRow> rows = store.generations();
            assertEquals(List.of(0, 1, 2), rows.stream().map(RunStore.GenerationRow::number).toList());
            for (RunStore.GenerationRow row : rows) {
                assertEquals(4, store.members(row.number(), ChessEvaluate.SCHEMA).size());
                assertEquals(12, row.games());
                assertEquals(12, store.games(row.number(), "population").size());
                assertEquals(4, store.games(row.number(), "yardstick").size()); // 2 openings, both colours
                assertEquals(4, row.yardstick().orElseThrow().games());
            }
            // generation 0 starts from the default weights
            assertEquals(ChessEvaluate.SCHEMA.defaults(), store.members(0, ChessEvaluate.SCHEMA).getFirst());
            // the example keeps the better half: generation 1 opens with generation 0's champion
            ParamVector champion = store.members(0, ChessEvaluate.SCHEMA).get(rows.getFirst().champion());
            assertEquals(champion, store.members(1, ChessEvaluate.SCHEMA).getFirst());
        }
    }

    @Test
    @DisplayName("A stopped and resumed run records exactly what an uninterrupted one does")
    void resumeIsSeamless(@TempDir Path dir) {
        List<Object> straight;
        try (RunStore store = RunStore.open(dir.resolve("straight.db"))) {
            EvolutionRunner.start(store, "tiny", example(), TINY, EvolutionRunner.Listener.SILENT, () -> false);
            straight = contents(store);
        }
        Path file = dir.resolve("resumed.db");
        AtomicInteger finished = new AtomicInteger();
        EvolutionRunner.Listener counting = new EvolutionRunner.Listener() {
            @Override
            public void generation(RunStore.GenerationRow row) {
                finished.incrementAndGet();
            }
        };
        try (RunStore store = RunStore.open(file)) {
            EvolutionRunner.start(store, "tiny", example(), TINY, counting, () -> finished.get() >= 1);
            assertEquals(1, store.generations().size());
            // a generation cut off half way: its games are thrown away and it is played again
            GameRecord stray = store.games(0, "population").getFirst();
            store.saveGame(1, "population", 0, 1, stray);
        }
        try (RunStore store = RunStore.open(file)) {
            EvolutionRunner.resume(store, example(), EvolutionRunner.Listener.SILENT, () -> false);
            assertEquals(straight, contents(store));
        }
    }

    @Test
    @DisplayName("The training export writes quiet positions with the game's result")
    void exportsPositions(@TempDir Path dir) throws IOException {
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "tiny", example(), new RunSettings(1, 1, 1, 2, 60, 2, 1, 0, 0),
                    EvolutionRunner.Listener.SILENT, () -> false);
            StringWriter out = new StringWriter();
            int lines = TrainingExport.write(store.allGames(), out);
            String[] rows = out.toString().split("\n");
            assertEquals("fen,result", rows[0]);
            assertEquals(lines, rows.length - 1);
            assertTrue(lines > 100, lines + " positions");
            for (int i = 1; i < rows.length; i++) {
                String[] parts = rows[i].split(",");
                Position.fromFen(parts[0]); // throws if not a FEN
                assertTrue(Set.of("1", "0", "0.5").contains(parts[1]), rows[i]);
            }
        }
    }

    @Test
    @DisplayName("A share of the games is played deeper, yardsticks too; the same weights listed twice play once")
    void deepGamesAndYardsticks(@TempDir Path dir) throws IOException {
        // the classic weights again under another name: the same weights, so one match
        Path copy = dir.resolve("copy.json");
        Files.writeString(copy, ChessEvaluate.CLASSIC.params().toJson());
        // 6 pairings × 2 openings = 12 units of two games; 50% of them at depth 2
        RunSettings settings = new RunSettings(2, 1, 2, 2, 80, 2, 3, 1, 2, List.of("classic", copy.toString()), 0, 2, 50, 50);
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "deep", example(), settings, EvolutionRunner.Listener.SILENT, () -> false);
            for (RunStore.GenerationRow row : store.generations()) {
                List<Integer> depths = store.gameDepths(row.number(), "population");
                assertEquals(24, depths.size());
                assertEquals(12, depths.stream().filter(d -> d == 2).count());
                assertEquals(12, depths.stream().filter(d -> d == 1).count());

                // classic and its copy are the same weights: one match, its result under both names
                assertEquals(4, store.games(row.number(), "yardstick").size());
                List<RunStore.YardstickResult> y = row.yardsticks();
                assertEquals(Set.of(1, 2), y.stream().filter(r -> r.opponent().equals("classic"))
                        .map(RunStore.YardstickResult::depth).collect(java.util.stream.Collectors.toSet()));
                assertEquals(RunStore.YardstickResult.combined(y, "classic"),
                        RunStore.YardstickResult.combined(y, copy.toString()));
                assertEquals(row.yardstick(), RunStore.YardstickResult.combined(y, "classic"));
                assertTrue(store.games(row.number(), "yardstick").stream()
                        .allMatch(g -> g.white().equals("classic") || g.black().equals("classic")));
            }
        }
    }

    @Test
    @DisplayName("An algorithm can set a pairing's depth itself")
    void pairingDepth(@TempDir Path dir) {
        evolution.Evolution deeper = new evolution.Evolution() {
            final RandomMutationExample inner = example();

            @Override
            public List<ParamVector> firstGeneration(ai.eval.ParamSchema schema, java.util.Random random) {
                return inner.firstGeneration(schema, random);
            }

            @Override
            public List<evolution.Pairing> pairings(List<ParamVector> population, java.util.Random random) {
                return List.of(new evolution.Pairing(0, 1, 2), new evolution.Pairing(2, 3));
            }

            @Override
            public List<ParamVector> nextGeneration(evolution.Generation generation, java.util.Random random) {
                return inner.nextGeneration(generation, random);
            }
        };
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "set", deeper, new RunSettings(1, 1, 1, 2, 60, 2, 1, 0, 0),
                    EvolutionRunner.Listener.SILENT, () -> false);
            assertEquals(List.of(2, 2, 1, 1), store.gameDepths(0, "population"));
        }
    }

    @Test
    @DisplayName("sf:auto starts at the lowest level, climbs above 70%, drops below 30%")
    void stockfishLevels() {
        java.util.function.BiFunction<Integer, arena.Score, RunStore.GenerationRow> row = (level, score) ->
                new RunStore.GenerationRow(0, 0, 0.5, 0, java.util.Optional.empty(), "",
                        List.of(new RunStore.YardstickResult(RunSettings.STOCKFISH_AUTO, level, 3, score)));
        assertEquals(1320, EvolutionRunner.stockfishLevel(List.of(), 0));
        assertEquals(1500, EvolutionRunner.stockfishLevel(List.of(row.apply(1320, new arena.Score("c", 8, 0, 2))), 1));
        assertEquals(1500, EvolutionRunner.stockfishLevel(List.of(row.apply(1700, new arena.Score("c", 2, 0, 8))), 1));
        assertEquals(1700, EvolutionRunner.stockfishLevel(List.of(row.apply(1700, new arena.Score("c", 5, 0, 5))), 1));
        assertEquals(1320, EvolutionRunner.stockfishLevel(List.of(row.apply(1320, new arena.Score("c", 0, 0, 10))), 1));
    }

    @Test
    @DisplayName("Stockfish as a yardstick, from the generation the settings say")
    void stockfishYardstick(@TempDir Path dir) {
        org.junit.jupiter.api.Assumptions.assumeTrue(engine.StockfishLocator.find().isPresent(), "Stockfish not installed");
        RunSettings settings = new RunSettings(2, 1, 1, 2, 60, 2, 1, 1, 1, List.of("default", "sf:auto"), 1, 0, 0, 0);
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "sf", example(), settings, EvolutionRunner.Listener.SILENT, () -> false);
            List<RunStore.GenerationRow> rows = store.generations();
            assertTrue(RunStore.YardstickResult.combined(rows.get(0).yardsticks(), "sf:auto").isEmpty());
            RunStore.YardstickResult sf = rows.get(1).yardsticks().stream()
                    .filter(y -> y.opponent().equals("sf:auto")).findFirst().orElseThrow();
            assertEquals(1320, sf.level());
            assertEquals("sf1320", sf.label());
            assertEquals(2, sf.score().games());
            assertEquals(2, store.games(1, "yardstick").stream()
                    .filter(g -> g.white().equals("sf1320") || g.black().equals("sf1320")).count());
        }
    }

    @Test
    @DisplayName("Every member plays Stockfish when the settings ask; the algorithm sees the scores, the level steps")
    void membersAgainstStockfish(@TempDir Path dir) {
        org.junit.jupiter.api.Assumptions.assumeTrue(engine.StockfishLocator.find().isPresent(), "Stockfish not installed");
        RunSettings settings = new RunSettings(2, 1, 1, 2, 40, 2, 1, 0, 0, List.of(), 0, 0, 0, 0, 1);
        List<Double> seen = new ArrayList<>();
        Evolution watching = new RandomMutationExample(3, 0.05, 0.01) {
            @Override
            public List<ParamVector> nextGeneration(Generation generation, java.util.Random random) {
                for (int i = 0; i < generation.size(); i++) {
                    seen.add(generation.stockfishScore(i));
                }
                assertEquals(1320, generation.stockfishLevel());
                return super.nextGeneration(generation, random);
            }
        };
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "sf members", watching, settings, EvolutionRunner.Listener.SILENT, () -> false);
            for (int number = 0; number < 2; number++) {
                List<GameRecord> games = store.games(number, "stockfish");
                assertEquals(3 * 2, games.size()); // 3 members, 1 opening, both colours
                for (int m = 0; m < 3; m++) {
                    String name = Generation.name(m);
                    assertEquals(2, games.stream().filter(g -> g.white().equals(name) || g.black().equals(name)).count());
                }
            }
            assertEquals(3, seen.size());
            assertTrue(seen.stream().noneMatch(s -> s.isNaN()));
        }
        assertEquals(1500, EvolutionRunner.stepLevel(1320, 0.75));
        assertEquals(1320, EvolutionRunner.stepLevel(1320, 0.1));
        assertEquals(1700, EvolutionRunner.stepLevel(1700, 0.5));
    }

    @Test
    @DisplayName("A run file from before yardstick lists and game depths still opens and reads")
    void olderRunFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("old.db");
        try (java.sql.Connection db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
             java.sql.Statement s = db.createStatement()) {
            s.execute("CREATE TABLE game (id INTEGER PRIMARY KEY, generation INTEGER, kind TEXT, white INTEGER,"
                    + " black INTEGER, opening TEXT, moves TEXT, result TEXT, reason TEXT, plies INTEGER, seed INTEGER)");
            s.execute("INSERT INTO game (generation, kind, white, black, opening, moves, result, reason, plies, seed)"
                    + " VALUES (0, 'yardstick', 3, -1, 'Italian', 'e2e4 e7e5', 'DRAW', 'PLY_CAP', 2, 7)");
        }
        try (RunStore store = RunStore.open(file)) {
            GameRecord g = store.games(0, "yardstick").getFirst();
            assertEquals("3", g.white());
            assertEquals("default", g.black());
            assertEquals(List.of(0), store.gameDepths(0, "yardstick"));
        }
    }

    @Test
    @DisplayName("A run plays a variant: antichess from zero, with random openings, the piece-set weights and a stop between games")
    void antichessFromZero(@TempDir Path dir) {
        RunSettings settings = new RunSettings(3, 1, 1, 20, 60, 2, 5, 1, 2, List.of("zero", "default"), 0, 0, 0, 0, 0,
                "antichess", null, 4, 6, java.util.Map.of("population", "4", "evolve", "material"));
        evolution.FromZero algorithm = new evolution.FromZero();
        try (RunStore store = RunStore.open(dir.resolve("anti.db"))) {
            java.util.concurrent.atomic.AtomicInteger games = new java.util.concurrent.atomic.AtomicInteger();
            EvolutionRunner.start(store, "anti", algorithm, settings, new EvolutionRunner.Listener() {
                @Override
                public void game(int generation, arena.GameRecord g) {
                    games.incrementAndGet();
                }
            }, () -> false, () -> games.get() >= 45); // 20 games a generation: stop inside generation 2

            RunStore.RunRow run = store.run().orElseThrow();
            ai.eval.ParamSchema schema = ai.eval.Evaluators.schema(ai.variant.Variants.ANTICHESS);
            assertEquals("antichess", run.settings().variantId());
            assertEquals("4", run.settings().algorithmOptions().get("population"));
            assertEquals("3", run.settings().algorithmOptions().get("survivors")); // the defaults are filled in
            List<RunStore.GenerationRow> rows = store.generations();
            assertEquals(2, rows.size(), "generation 2 was dropped by the stop");
            assertEquals(4, store.members(0, schema).size());
            // member 0 starts from nothing, the others around it
            assertEquals(0, store.members(0, schema).getFirst().get("material.queen"));
            assertTrue(store.members(0, schema).get(1).toArray().length == schema.size());
            // antichess games end with no pieces or no moves left, never by mate
            for (arena.GameRecord g : store.games(0, "population")) {
                assertTrue(g.reason().equals("NO_PIECES_LEFT") || g.reason().equals("NO_MOVES_LEFT")
                        || g.reason().equals("PLY_CAP") || g.reason().startsWith("DRAW"), g.reason());
                assertTrue(g.opening().startsWith("random "));
            }
            // "zero" and "default" are the same weights in antichess: the second yardstick copies the first
            assertEquals(2, rows.getFirst().yardsticks().size());
            assertEquals(rows.getFirst().yardsticks().get(0).score(), rows.getFirst().yardsticks().get(1).score());

            // resume plays generation 2 again and finishes
            EvolutionRunner.resume(store, new evolution.FromZero(), EvolutionRunner.Listener.SILENT, () -> false);
            assertEquals(3, store.generations().size());
            assertEquals(1, HallOfFame.besides(store.file()).list().size());
            HallOfFame.Entry last = HallOfFame.besides(store.file()).list().getFirst();
            assertEquals("antichess", last.game().id());
            assertEquals(schema, last.params().schema());
        }
    }

    @Test
    @DisplayName("Settings a run cannot play are refused: a variant from the chess opening book, or with Stockfish")
    void refusesImpossibleVariantSettings() {
        RunSettings book = new RunSettings(1, 1, 1, 20, 60, 1, 1, 0, 0, List.of("zero"), 0, 0, 0, 0, 0,
                "antichess", null, 0, 50, java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () -> EvolutionRunner.check(book));
        RunSettings stockfish = new RunSettings(1, 1, 1, 20, 60, 1, 1, 0, 0, List.of("sf:auto"), 0, 0, 0, 0, 0,
                "antichess", null, 4, 50, java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () -> EvolutionRunner.check(stockfish));
        assertThrows(IllegalArgumentException.class, () -> new RunSettings(1, 1, 1, 20, 60, 1, 1, 0, 0, List.of("zero"),
                0, 0, 0, 0, 0, "no-such-game", null, 4, 50, java.util.Map.of()).variant());
    }

    @Test
    @DisplayName("Outside chess the champion can be measured against the random mover and Fairy-Stockfish")
    void fairyAndRandomYardsticks(@TempDir Path dir) {
        ai.variant.Variant twoCheck = new ai.variant.Variant("two-check", "Two-check", ai.piece.StandardPieces.ALL,
                ai.piece.Grid.CHESS, ai.variant.Variants.CHESS.startFen(), List.of(ai.variant.WinCondition.checkmate(),
                ai.variant.WinCondition.checks(2), ai.variant.WinCondition.bareRoyal()), ai.variant.Variant.RoyalMode.ALL_SAFE,
                ai.variant.Variant.Stalemate.DRAW, true, 50, false, ai.variant.CastlingRule.CHESS); // bare royal: no Fairy config
        RunSettings made = new RunSettings(1, 1, 1, 20, 60, 1, 1, 1, 1, List.of("fsf"), 0, 0, 0, 0, 0,
                "two-check", ai.variant.VariantJson.write(twoCheck), 4, 50, java.util.Map.of());
        assertThrows(IllegalArgumentException.class, () -> EvolutionRunner.check(made), "Fairy-Stockfish does not know it");
        org.junit.jupiter.api.Assumptions.assumeTrue(arena.FairyStockfish.installed(), "Fairy-Stockfish is not installed");
        RunSettings settings = new RunSettings(1, 1, 1, 20, 120, 2, 5, 1, 1, List.of("random", "fsf:500"), 0, 0, 0, 0, 0,
                "antichess", null, 4, 6, java.util.Map.of("population", "4", "evolve", "material"));
        try (RunStore store = RunStore.open(dir.resolve("anti.db"))) {
            EvolutionRunner.start(store, "anti", new evolution.FromZero(), settings, EvolutionRunner.Listener.SILENT,
                    () -> false, () -> false);
            List<RunStore.YardstickResult> results = store.generations().getFirst().yardsticks();
            assertEquals(List.of("random", "fsf:500"), results.stream().map(RunStore.YardstickResult::opponent).toList());
            List<arena.GameRecord> games = store.games(0, "yardstick");
            assertEquals(4, games.size());
            for (arena.GameRecord g : games) { // Fairy-Stockfish's moves are legal antichess moves
                rules.Game replay = new rules.Game(ai.variant.Variants.ANTICHESS);
                g.moves().forEach(replay::play);
            }
        }
    }
}
