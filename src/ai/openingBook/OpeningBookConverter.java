package ai.openingBook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class OpeningBookConverter {

    public static void main(String[] args) {
        String inputFilePath = "D:\\Desktop\\סיכומים קורס תכנות\\אורט סינגאלובסקי\\java-projects\\chessGame_3\\src\\res\\opening_book\\M11.2_bin.txt"; // נתיב לקובץ המחולץ
        String outputFilePath = "D:\\Desktop\\סיכומים קורס תכנות\\אורט סינגאלובסקי\\java-projects\\chessGame_3\\src\\res\\opening_book\\opening_book.dat";

        OpeningBook openingBook = new OpeningBook();

        try {
            List<String> lines = Files.readAllLines(Paths.get(inputFilePath));

            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String fen = parts[0].trim();
                    String[] movesArray = parts[1].trim().split(",");
                    List<String> moves = new ArrayList<>();
                    for (String move : movesArray) {
                        moves.add(move.trim());
                    }
                    openingBook.addOpening(fen, moves);
                }
            }

            openingBook.saveToFile(outputFilePath);
            System.out.println("Opening book successfully converted and saved.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

