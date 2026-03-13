package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloRatingCalculatorTest {

    private final EloRatingCalculator calculator = new EloRatingCalculator(2, 24, 2, 2, 8, 100);

    @Test
    void computesPlacementWithTies() {
        Map<String, Integer> placements = this.calculator.computePlacements(List.of(
                new MatchScore("Alice", 30),
                new MatchScore("Bob", 20),
                new MatchScore("Carol", 20),
                new MatchScore("Dave", 10)
        ));

        assertEquals(1, placements.get("Alice"));
        assertEquals(2, placements.get("Bob"));
        assertEquals(2, placements.get("Carol"));
        assertEquals(4, placements.get("Dave"));
    }

    @Test
    void givesHigherDeltaToWinnerWithStreakBonus() {
        List<MatchScore> finalScores = List.of(new MatchScore("Alice", 20), new MatchScore("Bob", 10));
        Map<String, Integer> placements = new LinkedHashMap<>();
        placements.put("Alice", 1);
        placements.put("Bob", 2);

        Map<String, RatingSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put("Alice", new RatingSnapshot("Alice", 1000, 5, 3, 2));
        snapshots.put("Bob", new RatingSnapshot("Bob", 1000, 5, 2, 0));

        List<PlayerMatchUpdate> updates = this.calculator.computeUpdates(finalScores, placements, snapshots, 2);

        PlayerMatchUpdate aliceUpdate = updates.stream()
                .filter(update -> update.nickname().equals("Alice"))
                .findFirst()
                .orElseThrow();

        PlayerMatchUpdate bobUpdate = updates.stream()
                .filter(update -> update.nickname().equals("Bob"))
                .findFirst()
                .orElseThrow();

        assertTrue(aliceUpdate.eloDelta() > 0);
        assertTrue(bobUpdate.eloDelta() < 0);
        assertEquals(3, aliceUpdate.winStreakAfter());
    }
}
