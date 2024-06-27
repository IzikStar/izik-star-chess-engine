package ai;

import java.io.*;

public class StockfishEngine {
    private Process engineProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isEngineRunning;
    public String promotionChoice = null;

    // רמות קושי (0-20)
    private int skillLevel = 10;

    public boolean startEngine(String path) {
        try {
            engineProcess = new ProcessBuilder(path).start();
            reader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
            isEngineRunning = true;
            // System.out.println("Stockfish engine started successfully.");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            isEngineRunning = false;
            return false;
        }
    }

    public void stopEngine() {
        try {
            sendCommand("quit");
            reader.close();
            writer.close();
            engineProcess.destroy();
            isEngineRunning = false;
            // System.out.println("Stockfish engine stopped successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
            if (!isEngineRunning) {
                // System.out.println("Engine is not running. Restarting engine...");
                startEngine("D:\\Desktop\\סיכומים קורס תכנות\\אורט סינגאלובסקי\\java-projects\\chessGame_3\\src\\res\\stockfish\\stockfish-windows-x86-64.exe");
            }
            writer.write(command + "\n");
            writer.flush();
            // System.out.println("Sent command: " + command);
        } catch (IOException e) {
            // System.out.println("Failed to send command: " + command);
            e.printStackTrace();
        }
    }

    public String getOutput(long waitTime) {
        StringBuilder output = new StringBuilder();
        try {
            Thread.sleep(waitTime);
            while (reader.ready()) {
                String line = reader.readLine();
                if (line != null) {
                    output.append(line).append("\n");
                    // System.out.println("Received line: " + line);
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
        waitForOutput("readyok", 50);
    }

    public String getBestMove(String fen) {
        sendCommand("uci");
        waitForOutput("uciok", 50);

        sendCommand("isready");
        waitForOutput("readyok", 50);

        sendCommand("ucinewgame");
        waitForOutput("readyok", 50);

        // הגדרת רמת הקושי
        setSkillLevel(skillLevel);

        sendCommand("position fen " + fen);
        waitForOutput("readyok", 50);

        sendCommand("go movetime 100");
        String output = getOutput(100);

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
        if (engine.startEngine("D:\\Desktop\\סיכומים קורס תכנות\\אורט סינגאלובסקי\\java-projects\\chessGame_3\\src\\res\\stockfish\\stockfish-windows-x86-64.exe")) {
            // הגדרת רמת הקושי הרצויה
            engine.setSkillLevel(5); // רמה 5 לדוגמה

            String fen = "rnbqkbnr/ppppppPp/8/8/8/8/PPPPPP1P/RNBQKBNR w KQkq - 0 1";
            String bestMove = engine.getBestMove(fen);
            System.out.println("Best move: " + bestMove);
            engine.stopEngine();
        } else {
            System.out.println("Failed to start the engine.");
        }
    }
}
