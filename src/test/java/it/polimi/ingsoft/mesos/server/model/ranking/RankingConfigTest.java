package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingConfigTest {

    @Test
    void defaultConfigHasExpectedValues() {
        RankingConfig config = RankingConfig.defaultConfig();

        assertEquals(2, config.minPlayers());
        assertEquals(5, config.maxPlayers());
        assertEquals(1000, config.initialElo());
        assertEquals(100, config.minElo());
        assertEquals(24, config.baseK());
        assertEquals(2, config.kStepPerExtraPlayer());
        assertEquals(2, config.streakBonusStep());
        assertEquals(8, config.streakBonusCap());
        assertEquals(3, config.maxBusyRetries());
    }

    @Test
    void throwsOnInvalidConfigValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(1, 5, 1000, 100, 24, 2, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 1, 1000, 100, 24, 2, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 50, 100, 24, 2, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 1000, 100, 0, 2, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 1000, 100, 24, 2, -1, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 1000, 100, 24, 2, 2, 8, -1));
    }

    @Test
    void throwsWhenInitialEloIsLowerThanMinimumOrKStepIsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 99, 100, 24, 2, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 1000, 100, 24, -1, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 1000, 0, 24, 2, 2, 8, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingConfig(2, 5, 1000, 100, 24, 2, 2, -1, 3));
    }
}
