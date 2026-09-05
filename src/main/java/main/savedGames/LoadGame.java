package main.savedGames;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoadGame {

    public List<String> loadGameFromFile(String fileName) {
        List<String> fenList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fenList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fenList;
    }
}

