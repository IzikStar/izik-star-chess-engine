package lab;

import ai.BitBoard.BitBoardEvaluate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            all.add(store.members(row.number(), BitBoardEvaluate.SCHEMA));
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
                assertEquals(4, store.members(row.number(), BitBoardEvaluate.SCHEMA).size());
                assertEquals(12, row.games());
                assertEquals(12, store.games(row.number(), "population").size());
                assertEquals(4, store.games(row.number(), "yardstick").size()); // 2 openings, both colours
                assertEquals(4, row.yardstick().orElseThrow().games());
            }
            // generation 0 starts from the default weights
            assertEquals(BitBoardEvaluate.SCHEMA.defaults(), store.members(0, BitBoardEvaluate.SCHEMA).getFirst());
            // the example keeps the better half: generation 1 opens with generation 0's champion
            ParamVector champion = store.members(0, BitBoardEvaluate.SCHEMA).get(rows.getFirst().champion());
            assertEquals(champion, store.members(1, BitBoardEvaluate.SCHEMA).getFirst());
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
    void deepGamesAndYardsticks(@TempDir Path dir) {
        // 6 pairings × 2 openings = 12 units of two games; 50% of them at depth 2
        RunSettings settings = new RunSettings(2, 1, 2, 2, 80, 2, 3, 1, 2, List.of("default", "classic"), 0, 2, 50, 50);
        try (RunStore store = RunStore.open(dir.resolve("run.db"))) {
            EvolutionRunner.start(store, "deep", example(), settings, EvolutionRunner.Listener.SILENT, () -> false);
            for (RunStore.GenerationRow row : store.generations()) {
                List<Integer> depths = store.gameDepths(row.number(), "population");
                assertEquals(24, depths.size());
                assertEquals(12, depths.stream().filter(d -> d == 2).count());
                assertEquals(12, depths.stream().filter(d -> d == 1).count());

                // classic and default are the same weights today: one match, its result under both names
                assertEquals(4, store.games(row.number(), "yardstick").size());
                List<RunStore.YardstickResult> y = row.yardsticks();
                assertEquals(Set.of(1, 2), y.stream().filter(r -> r.opponent().equals("default"))
                        .map(RunStore.YardstickResult::depth).collect(java.util.stream.Collectors.toSet()));
                assertEquals(RunStore.YardstickResult.combined(y, "default"), RunStore.YardstickResult.combined(y, "classic"));
                assertEquals(row.yardstick(), RunStore.YardstickResult.combined(y, "default"));
                assertTrue(store.games(row.number(), "yardstick").stream()
                        .allMatch(g -> g.white().equals("default") || g.black().equals("default")));
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
}
