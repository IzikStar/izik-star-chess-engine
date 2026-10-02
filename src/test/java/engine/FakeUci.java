package engine;

import rules.Rules;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * A stand-in UCI engine for tests, run as its own process like Stockfish. Every command it
 * receives is appended to a log file. Modes: {@code normal} answers the first legal move;
 * {@code hang} answers only after {@code stop}; {@code crash} exits on {@code go};
 * {@code illegal} answers a move that is not legal.
 *
 * <p>Usage: {@code java -cp <test classpath> engine.FakeUci <mode> <logfile>}
 */
public final class FakeUci {

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        try (PrintWriter log = new PrintWriter(new FileWriter(args[1], true), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String fen = rules.Position.START_FEN;
            boolean searching = false;
            String line;
            while ((line = in.readLine()) != null) {
                log.println(line);
                if (line.equals("uci")) {
                    System.out.println("id name FakeUci");
                    System.out.println("uciok");
                } else if (line.equals("isready")) {
                    System.out.println("readyok");
                } else if (line.startsWith("position fen ")) {
                    fen = fenAfter(line.substring("position fen ".length()));
                } else if (line.startsWith("go")) {
                    switch (mode) {
                        case "crash" -> System.exit(3);
                        case "hang" -> searching = true;
                        case "illegal" -> System.out.println("bestmove a1a1");
                        default -> System.out.println("bestmove " + Rules.legalMoves(fen).get(0).toUci());
                    }
                } else if (line.equals("stop") && searching) {
                    searching = false;
                    System.out.println("bestmove " + Rules.legalMoves(fen).get(0).toUci());
                } else if (line.equals("quit")) {
                    return;
                }
                System.out.flush();
            }
        }
    }

    /** "{fen} [moves m1 m2 …]" → the position after the moves. */
    private static String fenAfter(String spec) {
        String[] parts = spec.split(" moves ");
        String fen = parts[0];
        if (parts.length > 1) {
            List<String> moves = Arrays.asList(parts[1].trim().split("\\s+"));
            for (String uci : moves) {
                fen = Rules.applyMove(fen, rules.ChessMove.fromUci(uci));
            }
        }
        return fen;
    }
}
