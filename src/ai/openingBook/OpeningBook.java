package ai.openingBook;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class OpeningBook {
    private final Map<String, String> bookData = new HashMap<>();

    public void loadFromFile(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    bookData.put(parts[0], parts[1]);
                }
            }
        }
    }

    public void saveToFile(String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<String, String> entry : bookData.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        }
    }

    public String getMoves(String positionHash) {
        return bookData.get(positionHash);
    }
}

