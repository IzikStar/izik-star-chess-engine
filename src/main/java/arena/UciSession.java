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

/** One running {@link ExternalEngine} for one game. */
final class UciSession implements AutoCloseable {

    private final ExternalEngine engine;
    private final Process process;
    private final BufferedReader in;
    private final PrintWriter out;

    UciSession(ExternalEngine engine) {
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
    ChessMove move(List<String> moves) {
        StringBuilder position = new StringBuilder("position startpos");
        if (!moves.isEmpty()) {
            position.append(" moves");
            moves.forEach(m -> position.append(' ').append(m));
        }
        out.println(position);
        out.println(engine.nodes() > 0 ? "go nodes " + engine.nodes() : "go movetime " + engine.moveMillis());
        String[] line = await("bestmove").split("\\s+");
        if (line.length < 2 || line[1].equals("(none)")) {
            throw new IllegalStateException(engine.command() + " gave no move after " + moves);
        }
        return ChessMove.fromUci(line[1]);
    }

    private String await(String prefix) {
        try {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.startsWith(prefix)) {
                    return line;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException(engine.command() + " stopped before '" + prefix + "'");
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
