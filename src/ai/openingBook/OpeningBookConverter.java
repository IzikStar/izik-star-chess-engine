package ai.openingBook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class OpeningBookConverter {

    public static void main(String[] args) {
        try {
            String binaryFilePath = "D:\\Desktop\\programing\\java-projects\\chessGame_3\\src\\res\\opening_book\\Book.bin"; // נתיב לקובץ BIN שלך
            String textFilePath = "D:\\Desktop\\programing\\java-projects\\chessGame_3\\src\\res\\opening_book\\Book.txt";   // נתיב לקובץ טקסט שיתקבל

            // קריאה מקובץ BIN
            Map<String, String> openingBookData = readBinaryFile(binaryFilePath);

            // כתיבה לקובץ טקסט
            writeTextFile(openingBookData, textFilePath);

            System.out.println("Conversion complete. Data saved to " + textFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> readBinaryFile(String filePath) throws IOException {
        Map<String, String> openingBookData = new HashMap<>();

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filePath))) {
            while (dis.available() > 0) {
                // קריאה של מידע מהקובץ BIN (הנחות לגבי פורמט)
                String positionHash = readString(dis); // הפוך למתאים לפורמט שלך
                String moves = readString(dis); // הפוך למתאים לפורמט שלך

                openingBookData.put(positionHash, moves);
            }
        }

        return openingBookData;
    }

    private static void writeTextFile(Map<String, String> data, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue());
                writer.newLine();
            }
        }
    }

    private static String readString(DataInputStream dis) throws IOException {
        try {
            int length = dis.readInt();
            if (length <= 0) {
                throw new IOException("Invalid string length: " + length);
            }
            byte[] bytes = new byte[length];
            dis.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (EOFException e) {
            throw new IOException("Unexpected end of file while reading string", e);
        }
    }

}
