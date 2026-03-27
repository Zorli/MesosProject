package it.polimi.ingsoft.mesos.server.model.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes placements and Elo updates for a completed match.
 */
final class EloRatingCalculator {
    private final int minPlayers;
    private final int baseK;
    private final int kStepPerExtraPlayer;
    private final int streakBonusStep;
    private final int streakBonusCap;
    private final int minElo;

    /**
     * Creates an Elo calculator with tunable rating parameters.
     */
    EloRatingCalculator(int minPlayers,
                        int baseK,
                        int kStepPerExtraPlayer,
                        int streakBonusStep,
                        int streakBonusCap,
                        int minElo) {
        this.minPlayers = minPlayers;
        this.baseK = baseK;
        this.kStepPerExtraPlayer = kStepPerExtraPlayer;
        this.streakBonusStep = streakBonusStep;
        this.streakBonusCap = streakBonusCap;
        this.minElo = minElo;
    }

    /**
     * Computes match placements from final scores.
     * Equal scores produce equal placement.
     *
     * @param finalScores match final scores
     * @return map nickname -> placement
     */
    Map<String, Integer> computePlacements(List<MatchScore> finalScores) {
        List<MatchScore> orderedScores = finalScores.stream()
                .sorted(Comparator.comparingInt(MatchScore::finalScore).reversed()
                        .thenComparing(MatchScore::nickname))
                .toList();

        Map<String, Integer> placements = new LinkedHashMap<>();
        int currentPlacement = 1;
        int previousScore = Integer.MIN_VALUE;

        for (int index = 0; index < orderedScores.size(); index++) {
            MatchScore score = orderedScores.get(index);

            if (index == 0 || score.finalScore() < previousScore) {
                currentPlacement = index + 1;
            }

            placements.put(score.nickname(), currentPlacement);
            previousScore = score.finalScore();
        }

        return placements;
    }

    /**
     * Computes per-player rating updates for a match.
     *
     * @param finalScores final scores
     * @param placements precomputed placements
     * @param snapshots pre-match player snapshots
     * @param playerCount number of players in the match
     * @return updates ready to be persisted
     */
    List<PlayerMatchUpdate> computeUpdates(List<MatchScore> finalScores,
                                           Map<String, Integer> placements,
                                           Map<String, RatingSnapshot> snapshots,
                                           int playerCount) {
        List<PlayerMatchUpdate> updates = new ArrayList<>();
        int kFactor = this.baseK + (playerCount - this.minPlayers) * this.kStepPerExtraPlayer;

        for (MatchScore score : finalScores) {
            RatingSnapshot snapshot = snapshots.get(score.nickname());
            int placement = placements.get(score.nickname());

            double expected = this.computeExpectedScore(score.nickname(), snapshots);
            double actual = this.computeActualScore(score.nickname(), placements);
            int baseDelta = (int) Math.round(kFactor * (actual - expected));

            boolean isWinner = placement == 1;
            int newWinStreak = isWinner ? snapshot.winStreak() + 1 : 0;
            int streakBonus = isWinner ? this.calculateWinStreakBonus(newWinStreak) : 0;
            int eloDelta = baseDelta + streakBonus;

            int eloAfter = Math.max(this.minElo, snapshot.elo() + eloDelta);
            int winsAfter = snapshot.wins() + (isWinner ? 1 : 0);
            int gamesAfter = snapshot.gamesPlayed() + 1;

            updates.add(new PlayerMatchUpdate(
                    score.nickname(),
                    score.finalScore(),
                    placement,
                    snapshot.elo(),
                    eloDelta,
                    eloAfter,
                    gamesAfter,
                    winsAfter,
                    newWinStreak
            ));
        }

        return updates;
    }

    /** Computes expected score as average pairwise probability against all opponents. */
    private double computeExpectedScore(String nickname, Map<String, RatingSnapshot> snapshots) {
        RatingSnapshot player = snapshots.get(nickname);
        int opponents = snapshots.size() - 1;

        if (opponents <= 0) {
            return 1.0;
        }

        double expectedSum = 0.0;

        for (RatingSnapshot opponent : snapshots.values()) {
            if (opponent.nickname().equals(nickname)) {
                continue;
            }

            expectedSum += 1.0 / (1.0 + Math.pow(10.0, (opponent.elo() - player.elo()) / 400.0));
        }

        return expectedSum / opponents;
    }

    /** Computes actual score from placements using 1/0.5/0 pairwise outcomes. */
    private double computeActualScore(String nickname, Map<String, Integer> placements) {
        int playerPlacement = placements.get(nickname);
        int opponents = placements.size() - 1;

        if (opponents <= 0) {
            return 1.0;
        }

        double actualSum = 0.0;

        for (Map.Entry<String, Integer> entry : placements.entrySet()) {
            if (entry.getKey().equals(nickname)) {
                continue;
            }

            int opponentPlacement = entry.getValue();

            if (playerPlacement < opponentPlacement) {
                actualSum += 1.0;
            } else if (playerPlacement == opponentPlacement) {
                actualSum += 0.5;
            }
        }

        return actualSum / opponents;
    }

    /** Computes capped streak bonus, starting from second consecutive win. */
    private int calculateWinStreakBonus(int newWinStreak) {
        if (newWinStreak < 2) {
            return 0;
        }

        return Math.min(this.streakBonusCap, (newWinStreak - 1) * this.streakBonusStep);
    }
}
