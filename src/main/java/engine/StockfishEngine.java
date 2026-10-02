package engine;

import rules.ChessMove;
import rules.Rules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Stockfish over UCI, as one long-lived session (Phase 4, docs/phase-4-research.md Fork C1).
 *
 * <ul>
 *   <li>The process starts on first use and the UCI handshake runs once. A reader thread moves
 *       Stockfish's output into a queue, so nothing polls with {@code sleep}.</li>
 *   <li>Each move sends the whole game ({@code position fen <start> moves …}), so Stockfish sees
 *       repetitions; {@code ucinewgame} is sent only when a different game starts.</li>
 *   <li>The UI's level sets Stockfish's "Skill Level" ({@code level - 1}, 0-20) and how long it
 *       thinks ({@link #moveTimeMs}).</li>
 *   <li>A cancelled request sends {@code stop}. A crashed or silent process is killed and started
 *       again on the next request; after {@link #MAX_FAILURES} failures in a row, or if the
 *       executable cannot be launched, the engine reports itself unavailable and the caller falls
 *       back to the built-in engine.</li>
 * </ul>
 *
 * <p>Requests are served one at a time ({@code synchronized}); {@link #close()} may be called from
 * any thread and ends a search in progress.
 */
public class StockfishEngine implements Engine {

    /**
     * Location of the Stockfish executable. Stockfish is not shipped with the repository; see
     * the README. Resolution order: the {@code -Dstockfish.path=...} system property, then the
     * {@code STOCKFISH_PATH} environment variable, then {@code engine/stockfish-windows-x86-64.exe}
     * relative to the working directory.
     */
    public static final String DEFAULT_ENGINE_PATH = resolveEnginePath();

    /** Consecutive failed requests after which Stockfish is given up on for this session. */
    static final int MAX_FAILURES = 3;
    /** How long to wait for {@code uciok} / {@code readyok}. */
    static final long HANDSHAKE_TIMEOUT_MS = 5000;
    /** Extra time allowed past the move time before Stockfish counts as not answering. */
    static final long BESTMOVE_GRACE_MS = 3000;
    /** End-of-output marker the reader thread puts in the queue. */
    private static final String EOF = "\u0000eof";

    private static String resolveEnginePath() {
        String path = System.getProperty("stockfish.path");
        if (path == null || path.isBlank()) {
            path = System.getenv("STOCKFISH_PATH");
        }
        if (path == null || path.isBlank()) {
            path = "engine/stockfish-windows-x86-64.exe";
        }
        return path;
    }

    /** Thinking time per UI level: Levels 8 / 9 / 10 (13 / 15 / 17) and hints (21). */
    static long moveTimeMs(int level) {
        if (level <= 13) {
            return 300;
        }
        if (level <= 15) {
            return 600;
        }
        return 1000;
    }

    static int skillFor(int level) {
        return Math.max(0, Math.min(20, level - 1));
    }

    private final List<String> command;

    private volatile Process process;
    private BufferedWriter writer;
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();
    private volatile boolean unavailable;
    private int failures;
    private int skill = -1;
    private String sessionStartFen;
    private int sessionPly;
    /** Set when the engine thread was interrupted mid-request; restored when the request ends. */
    private boolean interrupted;

    public StockfishEngine() {
        this(List.of(DEFAULT_ENGINE_PATH));
    }

    /** @param command the program and arguments that start a UCI engine */
    public StockfishEngine(List<String> command) {
        this.command = List.copyOf(command);
    }

    @Override
    public boolean isAvailable() {
        return !unavailable;
    }

    @Override
    public synchronized ChessMove bestMove(SearchRequest request) {
        if (unavailable || request.cancel().isCancelled() || !ensureStarted()) {
            return null;
        }
        try {
            ChessMove move = search(request);
            failures = 0;
            return move;
        } catch (UciFailure e) {
            if (request.cancel().isCancelled()) {
                kill(); // its state is unknown; start clean next time, but this is no engine fault
                return null;
            }
            if (process == null) {
                return null; // closed while searching
            }
            System.err.println("Stockfish: " + e.getMessage() + "; restarting it on the next move.");
            kill();
            if (++failures >= MAX_FAILURES) {
                System.err.println("Stockfish failed " + failures + " times in a row; using the built-in engine.");
                unavailable = true;
            }
            return null;
        } finally {
            if (interrupted) {
                interrupted = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        kill();
    }

    // ---- the session -------------------------------------------------------

    private ChessMove search(SearchRequest request) throws UciFailure {
        sync();
        if (!request.startFen().equals(sessionStartFen) || request.moves().size() < sessionPly) {
            send("ucinewgame");
            sync();
        }
        sessionStartFen = request.startFen();
        sessionPly = request.moves().size();

        int wantedSkill = skillFor(request.skillLevel());
        if (wantedSkill != skill) {
            send("setoption name Skill Level value " + wantedSkill);
            sync();
            skill = wantedSkill;
        }

        String moves = request.moves().stream().map(ChessMove::toUci).collect(Collectors.joining(" "));
        send("position fen " + request.startFen() + (moves.isEmpty() ? "" : " moves " + moves));
        long moveTime = moveTimeMs(request.skillLevel());
        send("go movetime " + moveTime);
        String uci = awaitBestMove(request.cancel(), moveTime + BESTMOVE_GRACE_MS);
        if (uci == null || request.cancel().isCancelled()) {
            return null;
        }
        try {
            ChessMove move = ChessMove.fromUci(uci);
            if (Rules.isLegal(request.fen(), move)) {
                return move;
            }
        } catch (IllegalArgumentException e) {
            // fall through
        }
        throw new UciFailure("answered an illegal move '" + uci + "' in " + request.fen());
    }

    /** Waits for {@code bestmove}; on cancel sends {@code stop} and returns {@code null}. */
    private String awaitBestMove(Cancellation cancel, long timeoutMs) throws UciFailure {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        boolean stopped = false;
        while (true) {
            if (!stopped && cancel.isCancelled()) {
                send("stop");
                stopped = true;
                deadline = System.nanoTime() + BESTMOVE_GRACE_MS * 1_000_000;
            }
            String line = next(20);
            if (line != null && line.startsWith("bestmove")) {
                String[] parts = line.trim().split("\\s+");
                return stopped || parts.length < 2 ? null : parts[1];
            }
            if (System.nanoTime() > deadline) {
                throw new UciFailure("no bestmove within " + timeoutMs + " ms");
            }
        }
    }

    /** Sends {@code isready} and drops everything before {@code readyok}, e.g. a stale bestmove. */
    private void sync() throws UciFailure {
        send("isready");
        await("readyok");
    }

    private void await(String expected) throws UciFailure {
        long deadline = System.nanoTime() + HANDSHAKE_TIMEOUT_MS * 1_000_000;
        while (System.nanoTime() < deadline) {
            String line = next(20);
            if (line != null && line.trim().equals(expected)) {
                return;
            }
        }
        throw new UciFailure("no '" + expected + "' within " + HANDSHAKE_TIMEOUT_MS + " ms");
    }

    /** The next output line, or {@code null} if none came within {@code ms}. */
    private String next(long ms) throws UciFailure {
        if (process == null) {
            throw new UciFailure("closed");
        }
        String line;
        try {
            line = output.poll(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // A cancelled job is also interrupted; the Cancellation flag decides what happens.
            interrupted = true;
            return null;
        }
        if (line == EOF) {
            throw new UciFailure("the process exited");
        }
        return line;
    }

    private void send(String line) throws UciFailure {
        try {
            writer.write(line + "\n");
            writer.flush();
        } catch (IOException e) {
            throw new UciFailure("could not write to the process (" + e.getMessage() + ")");
        }
    }

    // ---- process lifecycle --------------------------------------------------

    private boolean ensureStarted() {
        if (process != null && process.isAlive()) {
            return true;
        }
        kill();
        Process started;
        try {
            started = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            System.err.println("Stockfish not available at '" + command.get(0) + "' (" + e.getMessage()
                    + "). Falling back to the built-in engine; see README to install Stockfish.");
            unavailable = true;
            return false;
        }
        output.clear();
        process = started;
        writer = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> readAll(started), "stockfish-output");
        reader.setDaemon(true);
        reader.start();
        sessionStartFen = null;
        skill = -1;
        try {
            send("uci");
            await("uciok");
            sync();
            return true;
        } catch (UciFailure e) {
            System.err.println("'" + command.get(0) + "' does not speak UCI (" + e.getMessage()
                    + "). Falling back to the built-in engine.");
            kill();
            unavailable = true;
            return false;
        }
    }

    private void readAll(Process p) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (process == p) {
                    output.add(line);
                }
            }
        } catch (IOException e) {
            // the process went away
        }
        if (process == p) {
            output.add(EOF);
        }
    }

    private void kill() {
        Process p = process;
        process = null;
        if (p == null) {
            return;
        }
        try {
            BufferedWriter w = writer;
            if (w != null) {
                w.write("quit\n");
                w.flush();
            }
        } catch (IOException e) {
            // already gone
        }
        p.destroy();
    }

    private static final class UciFailure extends Exception {
        UciFailure(String message) {
            super(message);
        }
    }
}
