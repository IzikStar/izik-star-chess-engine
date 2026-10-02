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
}
