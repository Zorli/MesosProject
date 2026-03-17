package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpRankingServiceTest {

    @Test
    void noOpRankingServiceExposesNaiveBehaviour() {
        RankingService service = NoOpRankingService.INSTANCE;

        assertTrue(service.registerUser("alice"));
        assertTrue(service.isNicknameAvailable("alice"));
        assertEquals(List.of(), service.getLeaderboard());
        assertEquals(List.of(), service.getMatchHistory());
        assertEquals(List.of(), service.getMatchHistory(3));
    }

    @Test
    void recordMatchFailsWhenPersistenceIsDisabled() {
        RankingService service = NoOpRankingService.INSTANCE;

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.recordMatchAndGetRanking(
                        "game-1",
                        2,
                        Instant.now(),
                        "alice",
                        List.of()
                )
        );

        assertEquals("Ranking persistence is not available in no-db mode", exception.getMessage());
    }
}
