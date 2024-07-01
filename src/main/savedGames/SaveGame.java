package main.savedGames;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class SaveGame {

    public void saveGameToFile(List<String> fenList, String fileName) {
        try (FileWriter file = new FileWriter(fileName)) {
            for (String fen : fenList) {
                file.write(fen + "\n");
            }
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

