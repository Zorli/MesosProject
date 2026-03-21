package it.polimi.ingsoft.mesos.server.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EraTest {

    @Test
    void nextAdvancesUntilThirdEraAndThenStaysThere() {
        assertEquals(Era.second, Era.first.next());
        assertEquals(Era.third, Era.second.next());
        assertEquals(Era.third, Era.third.next());
    }
}
