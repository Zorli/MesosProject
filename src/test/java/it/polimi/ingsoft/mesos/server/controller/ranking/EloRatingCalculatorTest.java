package it.polimi.ingsoft.mesos.server.controller.ranking;

import it.polimi.ingsoft.mesos.server.controller.ranking.EloRatingCalculator;
import it.polimi.ingsoft.mesos.server.controller.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.controller.ranking.PlayerMatchUpdate;
import it.polimi.ingsoft.mesos.server.controller.ranking.RatingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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

    @Test
    void placementsAreSortedByScoreDescendingThenNickname() {
        Map<String, Integer> placements = this.calculator.computePlacements(List.of(
                new MatchScore("Carol", 20),
                new MatchScore("Alice", 20),
                new MatchScore("Bob", 20),
                new MatchScore("Dave", 10)
        ));

        assertIterableEquals(List.of("Alice", "Bob", "Carol", "Dave"), placements.keySet());
        assertEquals(1, placements.get("Alice"));
        assertEquals(1, placements.get("Bob"));
        assertEquals(1, placements.get("Carol"));
        assertEquals(4, placements.get("Dave"));
    }

    @Test
    void computeUpdatesAppliesTieOutcomesAndCapsWinStreakBonus() {
        List<MatchScore> finalScores = List.of(
                new MatchScore("Alice", 20),
                new MatchScore("Bob", 20),
                new MatchScore("Carol", 10)
        );
        Map<String, Integer> placements = new LinkedHashMap<>();
        placements.put("Alice", 1);
        placements.put("Bob", 1);
        placements.put("Carol", 3);

        Map<String, RatingSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put("Alice", new RatingSnapshot("Alice", 1000, 10, 7, 10));
        snapshots.put("Bob", new RatingSnapshot("Bob", 1000, 8, 4, 0));
        snapshots.put("Carol", new RatingSnapshot("Carol", 1000, 7, 3, 2));

        List<PlayerMatchUpdate> updates = this.calculator.computeUpdates(finalScores, placements, snapshots, 3);

        PlayerMatchUpdate alice = updates.stream().filter(update -> update.nickname().equals("Alice")).findFirst().orElseThrow();
        PlayerMatchUpdate bob = updates.stream().filter(update -> update.nickname().equals("Bob")).findFirst().orElseThrow();
        PlayerMatchUpdate carol = updates.stream().filter(update -> update.nickname().equals("Carol")).findFirst().orElseThrow();

        assertEquals(15, alice.eloDelta(), "Il delta deve sommare il base delta al bonus streak cappato");
        assertEquals(11, alice.winStreakAfter());
        assertEquals(1015, alice.eloAfter());
        assertEquals(8, alice.winsAfter());
        assertEquals(11, alice.gamesAfter());
        assertEquals(1, bob.winStreakAfter());
        assertEquals(7, bob.eloDelta(), "Un vincitore senza streak eredita solo il base delta");
        assertEquals(5, bob.winsAfter());
        assertEquals(9, bob.gamesAfter());
        assertTrue(carol.eloDelta() < 0);
        assertEquals(0, carol.winStreakAfter());
        assertEquals(3, carol.winsAfter());
        assertEquals(8, carol.gamesAfter());
    }

    @Test
    void computeUpdatesRespectsMinimumEloFloor() {
        List<MatchScore> finalScores = List.of(new MatchScore("Alice", 20), new MatchScore("Bob", 10));
        Map<String, Integer> placements = Map.of("Alice", 1, "Bob", 2);
        Map<String, RatingSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put("Alice", new RatingSnapshot("Alice", 400, 2, 1, 0));
        snapshots.put("Bob", new RatingSnapshot("Bob", 100, 2, 0, 0));

        List<PlayerMatchUpdate> updates = this.calculator.computeUpdates(finalScores, placements, snapshots, 2);
        PlayerMatchUpdate bob = updates.stream().filter(update -> update.nickname().equals("Bob")).findFirst().orElseThrow();

        assertEquals(100, bob.eloAfter(), "L'Elo non deve scendere sotto la soglia minima");
    }

    @Test
    void computeUpdatesForSinglePlayerUsesPerfectActualAndExpectedScores() {
        List<MatchScore> finalScores = List.of(new MatchScore("Alice", 42));
        Map<String, Integer> placements = Map.of("Alice", 1);
        Map<String, RatingSnapshot> snapshots = Map.of("Alice", new RatingSnapshot("Alice", 1000, 3, 1, 1));

        List<PlayerMatchUpdate> updates = this.calculator.computeUpdates(finalScores, placements, snapshots, 1);
        PlayerMatchUpdate alice = updates.getFirst();

        assertEquals(2, alice.eloDelta(), "Con un solo player resta solo il primo step di streak bonus");
        assertEquals(1002, alice.eloAfter());
        assertEquals(2, alice.winStreakAfter(), "La vittoria singola deve comunque incrementare la streak");
        assertEquals(2, alice.winsAfter());
        assertEquals(4, alice.gamesAfter());
    }

    @Test
    void secondConsecutiveWinStartsApplyingTheFirstStreakBonusStep() {
        List<MatchScore> finalScores = List.of(new MatchScore("Alice", 20), new MatchScore("Bob", 10));
        Map<String, Integer> placements = Map.of("Alice", 1, "Bob", 2);
        Map<String, RatingSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put("Alice", new RatingSnapshot("Alice", 1000, 5, 3, 1));
        snapshots.put("Bob", new RatingSnapshot("Bob", 1000, 5, 2, 0));

        List<PlayerMatchUpdate> updates = this.calculator.computeUpdates(finalScores, placements, snapshots, 2);
        PlayerMatchUpdate alice = updates.stream().filter(update -> update.nickname().equals("Alice")).findFirst().orElseThrow();

        assertEquals(2, alice.winStreakAfter());
        assertEquals(14, alice.eloDelta(), "Con streak=2 il bonus deve essere un singolo step da 2 punti oltre al base delta");
    }
}
