package arena;

import rules.ChessMove;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** One running {@link ExternalEngine} for one game (also the variant health check's Fairy-Stockfish games). */
public final class UciSession implements AutoCloseable {

    private final ExternalEngine engine;
    private final Process process;
    private final BufferedReader in;
    private final PrintWriter out;
    /** The score of the last {@code info} line before the last {@code bestmove}, or {@link #NO_SCORE}. */
    private int lastScore = NO_SCORE;

    /** {@link #lastScore()} when the engine reported none. */
    public static final int NO_SCORE = Integer.MIN_VALUE;
    /** A mate in N is reported as this many centipawns (minus N), so a nearer mate scores higher. */
    public static final int MATE_SCORE = 10_000;

    public UciSession(ExternalEngine engine) {
        this.engine = engine;
        try {
            process = new ProcessBuilder(engine.command()).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start " + engine.command(), e);
        }
        in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
        out.println("uci");
        await("uciok");
        for (Map.Entry<String, String> option : engine.options().entrySet()) {
            out.println("setoption name " + option.getKey() + " value " + option.getValue());
        }
        out.println("ucinewgame");
        out.println("isready");
        await("readyok");
    }

    /** The engine's move after {@code moves} (UCI) from the start position. */
    public ChessMove move(List<String> moves) {
        StringBuilder position = new StringBuilder("position startpos");
        if (!moves.isEmpty()) {
            position.append(" moves");
            moves.forEach(m -> position.append(' ').append(m));
        }
        out.println(position);
        out.println(engine.nodes() > 0 ? "go nodes " + engine.nodes() : "go movetime " + engine.moveMillis());
        lastScore = NO_SCORE;
        String[] line = await("bestmove").split("\\s+");
        if (line.length < 2 || line[1].equals("(none)")) {
            throw new IllegalStateException(engine.command() + " gave no move after " + moves);
        }
        return ChessMove.fromUci(line[1]);
    }

    /**
     * The engine's score for the position it last moved in, from the side it moved for, in
     * centipawns (a mate in N as {@code ±(MATE_SCORE - N)}); {@link #NO_SCORE} when it gave none.
     */
    public int lastScore() {
        return lastScore;
    }

    private String await(String prefix) {
        try {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.startsWith(prefix)) {
                    return line;
                }
                if (line.startsWith("info ")) {
                    readScore(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException(engine.command() + " stopped before '" + prefix + "'");
    }

    /** {@code info depth 12 ... score cp 35 ...} or {@code score mate -3}; other info lines leave the score. */
    private void readScore(String info) {
        String[] words = info.split("\\s+");
        for (int i = 0; i + 2 < words.length; i++) {
            if (words[i].equals("score")) {
                try {
                    int n = Integer.parseInt(words[i + 2]);
                    if (words[i + 1].equals("cp")) {
                        lastScore = n;
                    } else if (words[i + 1].equals("mate")) {
                        lastScore = n > 0 ? MATE_SCORE - n : -MATE_SCORE - n;
                    }
                } catch (NumberFormatException ignored) {
                    // not a score we know
                }
                return;
            }
        }
    }

    @Override
    public void close() {
        out.println("quit");
        try {
            if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
