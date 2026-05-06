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

    private static final int DEFAULT_MIN_PLAYERS = 2;
    private static final int DEFAULT_MAX_PLAYERS = 5;
    private static final int DEFAULT_INITIAL_ELO = 1000;
    private static final int DEFAULT_MIN_ELO = 100;
    private static final int DEFAULT_BASE_K = 24;
    private static final int DEFAULT_K_STEP_PER_EXTRA_PLAYER = 2;
    private static final int DEFAULT_STREAK_BONUS_STEP = 2;
    private static final int DEFAULT_STREAK_BONUS_CAP = 8;
    private static final int DEFAULT_MAX_BUSY_RETRIES = 3;

    /**
     * Returns the default ranking configuration used by the server model.
     *
     * @return default ranking configuration
     */
    public static RankingConfig defaultConfig() {
        return new RankingConfig(
                DEFAULT_MIN_PLAYERS,
                DEFAULT_MAX_PLAYERS,
                DEFAULT_INITIAL_ELO,
                DEFAULT_MIN_ELO,
                DEFAULT_BASE_K,
                DEFAULT_K_STEP_PER_EXTRA_PLAYER,
                DEFAULT_STREAK_BONUS_STEP,
                DEFAULT_STREAK_BONUS_CAP,
                DEFAULT_MAX_BUSY_RETRIES
        );
    }

    /**
     * Returns a copy of this configuration with a custom retry policy.
     *
     * @param maxBusyRetries maximum retries for transient DB failures
     * @return new configuration with same values and overridden retry policy
     */
    public RankingConfig withMaxBusyRetries(int maxBusyRetries) {
        return new RankingConfig(
                this.minPlayers,
                this.maxPlayers,
                this.initialElo,
                this.minElo,
                this.baseK,
                this.kStepPerExtraPlayer,
                this.streakBonusStep,
                this.streakBonusCap,
                maxBusyRetries
        );
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
