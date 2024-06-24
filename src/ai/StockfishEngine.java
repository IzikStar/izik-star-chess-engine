package ai;

import java.io.*;

public class StockfishEngine {
    private Process engineProcess;
    private BufferedReader reader;
    private BufferedWriter writer;

    public boolean startEngine(String path) {
        try {
            engineProcess = new ProcessBuilder(path).start();
            reader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
            System.out.println("Stockfish engine started successfully.");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stopEngine() {
        try {
            sendCommand("quit");
            reader.close();
            writer.close();
            engineProcess.destroy();
            System.out.println("Stockfish engine stopped successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
            writer.write(command + "\n");
            writer.flush();
            System.out.println("Sent command: " + command);
        } catch (IOException e) {
            System.out.println("Failed to send command: " + command);
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
                    System.out.println("Received line: " + line);
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    public String getBestMove(String fen) {
        sendCommand("uci");
        getOutput(100);
        sendCommand("isready");
        getOutput(100);
        sendCommand("ucinewgame");
        getOutput(100);
        sendCommand("position fen " + fen);
        getOutput(100);
        sendCommand("go movetime 5000");

        String output = getOutput(5000);
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("bestmove")) {
                return line.split(" ")[1];
            }
        }
        return "unknown";
    }
}
