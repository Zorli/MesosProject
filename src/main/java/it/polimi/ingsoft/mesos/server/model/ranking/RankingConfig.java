package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Immutable configuration for ranking validation, Elo update and database retry policy.
 *
 * @param minPlayers minimum supported players per match
 * @param maxPlayers maximum supported players per match
 * @param initialElo starting Elo for new users
 * @param minElo hard lower bound for Elo after updates
 * @param baseK base Elo K-factor
 * @param kStepPerExtraPlayer K-factor increment per extra player above minPlayers
 * @param streakBonusStep bonus points per extra consecutive win
 * @param streakBonusCap maximum streak bonus that can be applied
 * @param maxBusyRetries maximum retries for transient database failures
 */
public record RankingConfig(int minPlayers,
                            int maxPlayers,
                            int initialElo,
                            int minElo,
                            int baseK,
                            int kStepPerExtraPlayer,
                            int streakBonusStep,
                            int streakBonusCap,
                            int maxBusyRetries) {

    /**
     * Returns the default ranking configuration used by the server model.
     *
     * @return default ranking configuration
     */
    public static RankingConfig defaultConfig() {
        return new RankingConfig(2, 5, 1000, 100, 24, 2, 2, 8, 3);
    }

    /**
     * Validates configuration values.
     */
    public RankingConfig {
        if (minPlayers < 2) {
            throw new IllegalArgumentException("Min players must be >= 2");
        }

        if (maxPlayers < minPlayers) {
            throw new IllegalArgumentException("Max players must be >= min players");
        }

        if (initialElo <= 0 || minElo <= 0) {
            throw new IllegalArgumentException("Elo values must be positive");
        }

        if (initialElo < minElo) {
            throw new IllegalArgumentException("Initial elo must be >= min elo");
        }

        if (baseK <= 0 || kStepPerExtraPlayer < 0) {
            throw new IllegalArgumentException("K factors are not valid");
        }

        if (streakBonusStep < 0 || streakBonusCap < 0) {
            throw new IllegalArgumentException("Streak bonus values cannot be negative");
        }

        if (maxBusyRetries < 0) {
            throw new IllegalArgumentException("Max busy retries cannot be negative");
        }
    }
}
