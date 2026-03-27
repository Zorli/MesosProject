package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingDataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingDataExceptionTest {

    @Test
    void containsProvidedErrorMessage() {
        RankingDataException exception = new RankingDataException("db failure");

        assertEquals("db failure", exception.getMessage());
    }
}
