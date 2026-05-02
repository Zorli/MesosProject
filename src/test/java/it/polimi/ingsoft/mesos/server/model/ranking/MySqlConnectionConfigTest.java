package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.MySqlConnectionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlConnectionConfigTest {

    @Test
    void storesValidConnectionSettings() {
        MySqlConnectionConfig config = new MySqlConnectionConfig(
                "jdbc:mysql://localhost:3306/mesos",
                "mesos_user",
                "secret"
        );

        assertEquals("jdbc:mysql://localhost:3306/mesos", config.jdbcUrl());
        assertEquals("mesos_user", config.username());
        assertEquals("secret", config.password());
    }

    @Test
    void rejectsBlankJdbcUrlBlankUsernameAndNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> new MySqlConnectionConfig(" ", "mesos_user", "secret"));
        assertThrows(IllegalArgumentException.class, () -> new MySqlConnectionConfig("jdbc:mysql://localhost:3306/mesos", " ", "secret"));
        assertThrows(IllegalArgumentException.class, () -> new MySqlConnectionConfig("jdbc:mysql://localhost:3306/mesos", "mesos_user", null));
    }
}
