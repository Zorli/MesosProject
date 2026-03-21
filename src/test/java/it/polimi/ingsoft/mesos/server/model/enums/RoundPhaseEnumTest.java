package it.polimi.ingsoft.mesos.server.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoundPhaseEnumTest {

    @Test
    void legacyRoundPhaseEnumExposesStableOrder() {
        assertArrayEquals(
                new RoundPhase[]{
                        RoundPhase.PLAYER_TURN,
                        RoundPhase.LOWER_ROW_EVENTS,
                        RoundPhase.RETURN_TO_STARTING_TILE,
                        RoundPhase.STARTING_TILE_EFFECTS,
                        RoundPhase.ROW_CLEANUP,
                        RoundPhase.ROW_REFILL,
                        RoundPhase.ERA_ADVANCEMENT
                },
                RoundPhase.values()
        );
    }
}
