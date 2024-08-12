package ai.openingBook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BinaryFileReader {

    public static void main(String[] args) {
        String filePath = "D:\\Desktop\\M11.2.bin";
        File file = new File(filePath);

        if (file.exists()) {
            System.out.println("File exists!");
        } else {
            System.out.println("File does not exist!");
        }

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

