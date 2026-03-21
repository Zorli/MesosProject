package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresConnectionConfigTest {

    @Test
    void storesValidConnectionSettings() {
        PostgresConnectionConfig config = new PostgresConnectionConfig(
                "jdbc:postgresql://localhost:5432/mesos",
                "mesos_user",
                "secret"
        );

        assertEquals("jdbc:postgresql://localhost:5432/mesos", config.jdbcUrl());
        assertEquals("mesos_user", config.username());
        assertEquals("secret", config.password());
    }

    @Test
    void rejectsBlankJdbcUrlBlankUsernameAndNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresConnectionConfig(" ", "mesos_user", "secret"));
        assertThrows(IllegalArgumentException.class, () -> new PostgresConnectionConfig("jdbc:postgresql://localhost:5432/mesos", " ", "secret"));
        assertThrows(IllegalArgumentException.class, () -> new PostgresConnectionConfig("jdbc:postgresql://localhost:5432/mesos", "mesos_user", null));
    }
}
