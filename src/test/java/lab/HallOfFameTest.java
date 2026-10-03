package lab;

import ai.BitBoard.BitBoardEvaluate;
import arena.Players;
import evolution.RandomMutationExample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rules.Pgn;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HallOfFameTest {

    @Test
    @DisplayName("A run keeps its last champion; kept members play as hof:NAME; games export as PGN")
    void keepsAndExports(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("try.db");
        try (RunStore store = RunStore.open(file)) {
            EvolutionRunner.start(store, "Try 1", new RandomMutationExample(4, 0.2, 0.05),
                    new RunSettings(2, 1, 1, 2, 60, 2, 1, 1, 1), EvolutionRunner.Listener.SILENT, () -> false);
            HallOfFame hall = HallOfFame.besides(file);
            assertEquals(dir.resolve(HallOfFame.FOLDER).toAbsolutePath(), hall.dir());
            int champion = store.generations().getLast().champion();
            HallOfFame.Entry last = hall.get(HallOfFame.nameFor("Try 1", 1, champion)).orElseThrow();
            assertTrue(last.reason().startsWith("last champion"), last.reason());
            assertEquals(store.members(1, BitBoardEvaluate.SCHEMA).get(champion), last.params());
            assertFalse(last.yardsticks().isEmpty());
            // its generation's population games (3 opponents × 2 colours) and its yardstick games (2)
            assertEquals(8, last.pgn().split("\\[Event ", -1).length - 1);

            hall.save(HallOfFame.fromRun(store, file, 0, 3, "steady", "kept by hand"));
            assertEquals(store.members(0, BitBoardEvaluate.SCHEMA).get(3), Players.params("hof:steady", hall.dir()));
            assertEquals(List.of("steady", last.name()), hall.list().stream().map(HallOfFame.Entry::name).toList());

            StringWriter out = new StringWriter();
            int games = RunPgn.write(store, -1, -1, out);
            assertEquals(2 * (12 + 2), games);
            for (String game : out.toString().split("\n\n(?=\\[Event)")) {
                Pgn.Parsed parsed = Pgn.read(game); // every game reads back, every move legal
                assertTrue(parsed.tags().containsKey("Depth"));
            }
        }
    }

    @Test
    @DisplayName("A champion is kept when it beat a yardstick: its whole interval above 0")
    void reasons() {
        RunStore.GenerationRow strong = new RunStore.GenerationRow(3, 0, 0.6, 10, java.util.Optional.empty(), "",
                List.of(new RunStore.YardstickResult("classic", 0, 3, new arena.Score("champion", 40, 0, 10)),
                        new RunStore.YardstickResult("sf:auto", 1500, 3, new arena.Score("champion", 5, 0, 5))));
        assertEquals(List.of("beat classic"), HallOfFame.reasonsToKeep(strong, false));
        assertEquals(List.of("last champion", "beat classic"), HallOfFame.reasonsToKeep(strong, true));
    }
}
