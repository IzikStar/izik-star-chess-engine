package ai.openingBook;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpeningBook implements Serializable {
    private Map<String, List<String>> book;

    public OpeningBook() {
        book = new HashMap<>();
    }

    public void addOpening(String fen, List<String> moves) {
        book.put(fen, moves);
    }

    public List<String> getMoves(String fen) {
        return book.get(fen);
    }

    public void saveToFile(String filename) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this);
        }
    }

    public static OpeningBook loadFromFile(String filename) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            return (OpeningBook) in.readObject();
        }
    }
}
