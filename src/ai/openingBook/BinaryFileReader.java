package ai.openingBook;

import java.io.FileInputStream;
import java.io.IOException;

public class BinaryFileReader {

    public static void main(String[] args) {
        String filePath = "D:\\Desktop\\סיכומים קורס תכנות\\אורט סינגאלובסקי\\java-projects\\chessGame_3\\src\\res\\opening_book\\M11.2_bin";

        try (FileInputStream fis = new FileInputStream(filePath)) {
            int byteContent;
            while ((byteContent = fis.read()) != -1) {
                System.out.print(String.format("%02X ", byteContent));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

