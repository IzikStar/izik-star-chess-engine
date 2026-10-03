package analysis;

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

/**
 * A UCI engine (Stockfish) used as a judge: its own process, at full strength, searched to a fixed
 * depth. Kept apart from {@code engine.StockfishEngine}, which plays games at a reduced skill
 * level, so analysing never changes how the opponent plays and the two never wait on each other.
 */
public final class UciEvaluator implements AutoCloseable {

    /** How long one position may take before the engine counts as hung. */
    private static final long POSITION_TIMEOUT_MS = 60_000;
    private static final long HANDSHAKE_TIMEOUT_MS = 5_000;
    private static final String EOF = "\u0000eof";

    /** A score from White's point of view: centipawns, or moves to mate (positive: White mates). */
    public record Score(Integer cp, Integer mate) {
        public static Score cp(int cp) {
            return new Score(cp, null);
        }

        public static Score mate(int mate) {
            return new Score(null, mate);
        }

        Score negate() {
            return cp != null ? cp(-cp) : mate(-mate);
        }
    }

    /** The engine's verdict on one position: its score (White's view) and its best move, UCI (null if none). */
    public record Verdict(Score score, String bestMove) {}

    private final Process process;
    private final BufferedWriter writer;
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();

    public UciEvaluator(List<String> command) throws IOException {
        process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::readAll, "analysis-engine-output");
        reader.setDaemon(true);
        reader.start();
        send("uci");
        await("uciok", HANDSHAKE_TIMEOUT_MS);
        send("setoption name Threads value " + Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)));
        send("setoption name Hash value 64");
        send("ucinewgame");
        send("isready");
        await("readyok", HANDSHAKE_TIMEOUT_MS);
    }

    /**
     * Searches the position after {@code moves} (UCI) from {@code startFen} to {@code depth}.
     * Sending the moves, not the position's FEN, lets the engine see repetitions.
     */
    public Verdict evaluate(String startFen, List<String> moves, int depth, boolean whiteToMove) throws IOException {
        send("position fen " + startFen + (moves.isEmpty() ? "" : " moves " + String.join(" ", moves)));
        send("go depth " + depth);
        Score last = null;
        long deadline = System.nanoTime() + POSITION_TIMEOUT_MS * 1_000_000;
        while (true) {
            String line = next(deadline);
            if (line.startsWith("info") && line.contains(" score ") && !line.contains(" lowerbound") && !line.contains(" upperbound")
                    && (!line.contains(" multipv ") || line.contains(" multipv 1 "))) {
                Score s = parseScore(line);
                if (s != null) {
                    last = s;
                }
            } else if (line.startsWith("bestmove")) {
                String[] parts = line.trim().split("\\s+");
                String best = parts.length > 1 && !parts[1].equals("(none)") ? parts[1] : null;
                if (last == null) {
                    throw new IOException("no score for the position");
                }
                return new Verdict(whiteToMove ? last : last.negate(), best);
            }
        }
    }

    /** The score in an {@code info} line, from the side to move's point of view. */
    static Score parseScore(String info) {
        String[] t = info.trim().split("\\s+");
        for (int i = 0; i + 2 < t.length; i++) {
            if (t[i].equals("score")) {
                int value = Integer.parseInt(t[i + 2]);
                return t[i + 1].equals("mate") ? Score.mate(value) : Score.cp(value);
            }
        }
        return null;
    }

    private void send(String line) throws IOException {
        writer.write(line + "\n");
        writer.flush();
    }

    private void await(String expected, long timeoutMs) throws IOException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        while (!next(deadline).trim().equals(expected)) {
            // skip
        }
    }

    private String next(long deadlineNanos) throws IOException {
        long left = deadlineNanos - System.nanoTime();
        String line;
        try {
            line = left <= 0 ? null : output.poll(left, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (line == null) {
            throw new IOException("the analysis engine did not answer in time");
        }
        if (line == EOF) {
            throw new IOException("the analysis engine exited");
        }
        return line;
    }

    private void readAll() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                output.add(line);
            }
        } catch (IOException e) {
            // the process went away
        }
        output.add(EOF);
    }

    @Override
    public void close() {
        try {
            send("quit");
        } catch (IOException e) {
            // already gone
        }
        process.destroy();
    }
}
