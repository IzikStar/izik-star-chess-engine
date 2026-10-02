package engine;

import rules.ChessMove;
import rules.Rules;

import java.io.*;

/**
 * Stockfish over UCI. As an {@link Engine} it maps the UI's 0-18 level onto Stockfish's
 * "Skill Level" as {@code level - 1} (Phase 3 Fork 6; before, a never-written static forced 0).
 * The per-move UCI handshake and polling are unchanged here — that is Phase 4 work.
 */
public class StockfishEngine implements Engine {

    /**
     * Location of the Stockfish executable. Stockfish is not shipped with the repository; see
     * the README. Resolution order: the {@code -Dstockfish.path=...} system property, then the
     * {@code STOCKFISH_PATH} environment variable, then {@code engine/stockfish-windows-x86-64.exe}
     * relative to the working directory.
     */
    public static final String DEFAULT_ENGINE_PATH = resolveEnginePath();

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

    private Process engineProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isEngineRunning;
    /** Set once the executable could not be launched; we then stop retrying and report unavailable. */
    private boolean startFailed;
    public int skillLevel = 0;

    /** How long {@link #bestMove} keeps re-asking for a legal move before giving up. */
    private static final long RETRY_BUDGET_MS = 1200;

    @Override
    public ChessMove bestMove(SearchRequest request) {
        String fen = request.fen();
        int level = request.skillLevel();
        long start = System.currentTimeMillis();
        while (isAvailable() && !request.cancel().isCancelled()
                && System.currentTimeMillis() - start < RETRY_BUDGET_MS) {
            skillLevel = Math.max(0, Math.min(20, level - 1));
            String uci = getBestMove(fen);
            if (uci == null || uci.equals("unknown") || uci.equals("(none)")) {
                continue;
            }
            try {
                ChessMove move = ChessMove.fromUci(uci);
                if (Rules.isLegal(fen, move)) {
                    return move;
                }
            } catch (IllegalArgumentException e) {
                // malformed reply — ask again
            }
        }
        return null;
    }

    @Override
    public void close() {
        stopEngine();
    }

    public boolean startEngine(String path) {
        try {
            engineProcess = new ProcessBuilder(path).start();
            reader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
            isEngineRunning = true;
            return true;
        } catch (IOException e) {
            System.err.println("Stockfish not available at '" + path + "' (" + e.getMessage()
                    + "). Falling back to the built-in engine; see README to install Stockfish.");
            isEngineRunning = false;
            startFailed = true;
            return false;
        }
    }

    /** False once launching the Stockfish executable has failed (e.g. it was not downloaded). */
    @Override
    public boolean isAvailable() {
        return !startFailed;
    }

    private boolean ensureRunning() {
        if (isEngineRunning) return true;
        if (startFailed) return false;
        return startEngine(DEFAULT_ENGINE_PATH);
    }

    public void stopEngine() {
        if (!isEngineRunning) return;
        try {
            sendCommand("quit");
            reader.close();
            writer.close();
            engineProcess.destroy();
            isEngineRunning = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
            if (!ensureRunning()) {
                return;
            }
            writer.write(command + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getOutput(long waitTime) {
        StringBuilder output = new StringBuilder();
        try {
            Thread.sleep(waitTime);
            while (reader != null && reader.ready()) {
                String line = reader.readLine();
                if (line != null) {
                    output.append(line).append("\n");
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    public void setSkillLevel(int skillLevel) {
        this.skillLevel = skillLevel;
        sendCommand("setoption name Skill Level value " + skillLevel);
        waitForOutput("readyok", 10);
    }

    public String getBestMove(String fen) {
        if (!ensureRunning()) {
            return "unknown";
        }
        sendCommand("uci");
        waitForOutput("uciok", 10);

        sendCommand("isready");
        waitForOutput("readyok", 10);

        sendCommand("ucinewgame");
        waitForOutput("readyok", 10);

        setSkillLevel(skillLevel);

        sendCommand("position fen " + fen);
        waitForOutput("readyok", 10);

        long startTime = System.currentTimeMillis();
        sendCommand("go movetime 150 nodes 100000000"); // הגבלת זמן ומספר הצמתים
        String output = getOutput(180);
        long endTime = System.currentTimeMillis();
        System.out.println("Calculation time: " + (endTime - startTime) + " ms");


        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("bestmove")) {
                return line.split(" ")[1];
            }
        }
        return "unknown";
    }

    private void waitForOutput(String expectedOutput, long waitTime) {
        long startTime = System.currentTimeMillis();
        String output;
        do {
            output = getOutput(100);
            if (output.contains(expectedOutput)) {
                return;
            }
        } while (System.currentTimeMillis() - startTime < waitTime);
    }

    public static void main(String[] args) {
        StockfishEngine engine = new StockfishEngine();
        if (engine.startEngine(DEFAULT_ENGINE_PATH)) {
            engine.setSkillLevel(10); // רמה 5 לדוגמה

            String fen = "rnbqkbnr/ppppppPp/8/8/8/8/PPPPPP1P/RNBQKBNR w KQkq - 0 1";
            String bestMove = engine.getBestMove(fen);
            System.out.println("Best move: " + bestMove);

            String output = engine.getOutput(500);
            if (output.contains("mate")) {
                System.out.println("Mate in sight!");
            }

            engine.stopEngine();
        } else {
            System.out.println("Failed to start the engine.");
        }
    }

}
