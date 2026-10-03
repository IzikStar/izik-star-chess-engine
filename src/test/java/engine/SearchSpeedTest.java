package engine;

import ai.BitBoard.BitBoardRules;
import ai.Minimax;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import rules.Position;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The speed and memory benchmark of Phase 4b (docs/phase-4b-research.md §6 items 3 and 4), run
 * with {@code mvn test -Pstress}. In six benchmark positions, Level 6 finishes its full depth
 * within 1 s, so it never falls back to a shallower move. That is depths 5 and 6, or 7 and 8 in the
 * rook endgame.
 *
 * <p>Level 7 has to finish within the engine's 5 s cap. Phase 4b had it there; the quiescence search
 * of Phase 5 pushed the busiest benchmark (the middlegame) to about 6 s at depth 6, so the limit was
 * twice the cap for a while. The transposition table and move ordering of Phase 5b brought it back
 * to about 1.2 s (docs/phase-5b-research.md §4), and the limit back to the cap.
 *
 * <p>The searches run in a separate JVM with a 300 MB heap, so a search that needs more memory
 * fails with {@code OutOfMemoryError}. Before Phase 4b one search held the whole searched tree,
 * gigabytes at depth 5. The limits were set on the cloud container (4 cores, JDK 21); a much
 * slower machine may miss them.
 */
@Tag("stress")
class SearchSpeedTest {

    record Benchmark(String name, String fen) {}

    static final List<Benchmark> POSITIONS = List.of(
            new Benchmark("start", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
            new Benchmark("after-1.e4", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
            new Benchmark("italian", "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 6 5"),
            new Benchmark("middlegame", "r2q1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PP3PPP/R2Q1RK1 w - - 0 10"),
            new Benchmark("kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"),
            new Benchmark("rook-endgame", "8/5pk1/6p1/8/3R4/6P1/5PK1/3r4 w - - 0 40"));

    /** Level as shown, level passed to the engine (the same since the 0-13 ladder) and time limit. */
    record Level(int level, int skill, long limitMs) {}

    static final List<Level> LEVELS = List.of(
            new Level(6, 6, 1000),
            new Level(7, 7, MinimaxEngine.TIME_CAP_MS));

    static final String HEAP = "-Xmx300m";

    private static final Pattern RESULT =
            Pattern.compile("^(\\S+) level (\\d+) depth (\\d+): (\\d+) ms$", Pattern.MULTILINE);

    @Test
    @DisplayName("Levels 6 and 7 finish their full depth in time, in a 300 MB heap")
    void levelsSixAndSevenFinishInTime(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("benchmark.txt");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                HEAP, "-cp", classPath(), SearchSpeedTest.class.getName())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        boolean finished = child.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            child.destroyForcibly().waitFor();
        }
        String output = Files.readString(log);
        System.out.print(output); // the measurements end up in the surefire report
        assertTrue(finished, "the benchmark did not finish:\n" + output);
        assertEquals(0, child.exitValue(), "the benchmark failed:\n" + output);

        List<Executable> checks = new ArrayList<>();
        Matcher m = RESULT.matcher(output);
        while (m.find()) {
            String result = m.group();
            long ms = Long.parseLong(m.group(4));
            long limitMs = level(Integer.parseInt(m.group(2))).limitMs();
            checks.add(() -> assertTrue(ms <= limitMs, result + ", over the " + limitMs + " ms limit"));
        }
        assertEquals(POSITIONS.size() * LEVELS.size(), checks.size(), "results are missing:\n" + output);
        assertAll(checks);
    }

    private static Level level(int level) {
        return LEVELS.stream().filter(l -> l.level() == level).findFirst().orElseThrow();
    }

    /** The test class path: surefire's own property when it runs the test, else the JVM's. */
    private static String classPath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    /**
     * Runs in the child JVM. Prints, for each position and level, how long the search takes to
     * finish the level's depth, deepening one ply at a time as the engine does. Each time is the
     * faster of two runs, so one garbage collection or a busy core does not decide the result.
     */
    public static void main(String[] args) {
        for (Benchmark b : POSITIONS) {
            Minimax.getBestMove(BitBoardRules.fromFen(b.fen()), 4); // let the JIT compile the search first
        }
        for (Benchmark b : POSITIONS) {
            for (Level level : LEVELS) {
                int depth = MinimaxEngine.searchDepth(Position.fromFen(b.fen()), level.skill());
                long ms = Math.min(millisToFinish(b.fen(), depth), millisToFinish(b.fen(), depth));
                System.out.println(b.name() + " level " + level.level() + " depth " + depth + ": " + ms + " ms");
            }
        }
    }

    private static long millisToFinish(String fen, int depth) {
        long start = System.nanoTime();
        Minimax.getBestMove(BitBoardRules.fromFen(fen), depth);
        return (System.nanoTime() - start) / 1_000_000;
    }
}
