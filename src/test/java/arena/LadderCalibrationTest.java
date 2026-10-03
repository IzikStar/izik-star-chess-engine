package arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LadderCalibrationTest {

    private static LadderCalibration.Tally tally(int wins, int draws, int losses) {
        LadderCalibration.Tally t = new LadderCalibration.Tally();
        t.wins = wins;
        t.draws = draws;
        t.losses = losses;
        return t;
    }

    @Test
    @DisplayName("Each level meets the next one up, and from Level 3 the anchors around it")
    void pairings() {
        List<LadderCalibration.Pairing> pairings = LadderCalibration.pairings();
        assertEquals(new LadderCalibration.Pairing("L0", "L1"), pairings.get(0));
        assertTrue(pairings.contains(new LadderCalibration.Pairing("L12", "L13")));
        assertTrue(pairings.contains(new LadderCalibration.Pairing("L8", "SF1900")));
        assertTrue(pairings.contains(new LadderCalibration.Pairing("L8", "SF2100")));
        assertEquals(List.of(1320), LadderCalibration.anchorsAround(750));
        assertEquals(List.of(1900, 2100), LadderCalibration.anchorsAround(1910));
        assertEquals(List.of(3190), LadderCalibration.anchorsAround(3190));
    }

    @Test
    @DisplayName("The fit finds the ratings behind the scores, anchors fixed")
    void fit() {
        Map<String, LadderCalibration.Tally> tallies = new LinkedHashMap<>();
        // 76% is +200 Elo, 24% is -200
        tallies.put("L5 SF1500", tally(76, 0, 24));
        tallies.put("L5 SF1900", tally(24, 0, 76));
        tallies.put("L4 L5", tally(24, 0, 76));
        Map<String, Double> elo = LadderCalibration.fit(tallies);
        assertEquals(1500, elo.get("SF1500"));
        assertEquals(1700, elo.get("L5"), 15);
        assertEquals(1500, elo.get("L4"), 25);
    }
}
