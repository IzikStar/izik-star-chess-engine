package main.savedGames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SavedStatesForDraws {
    private static ArrayList<String> states = new ArrayList<>();

    public static void addState(String state) {
        states.add(state);
    }

    public static void clear() {
        states.clear();
    }

    public static boolean isRepetition() {
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (String state : states) {
            frequencyMap.put(state, frequencyMap.getOrDefault(state, 0) + 1);

            if (frequencyMap.get(state) == 3) {
                return true;
            }
        }

        return false;
    }
}
