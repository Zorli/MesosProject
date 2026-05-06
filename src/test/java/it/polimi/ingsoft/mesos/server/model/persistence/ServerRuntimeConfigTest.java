package it.polimi.ingsoft.mesos.server.model.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerRuntimeConfigTest {

    @Test
    void defaultsPointToLocalMySql() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of());

        assertEquals(
                "jdbc:mysql://localhost:3306/mesos?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
                config.jdbcUrl()
        );
        assertEquals(
                "jdbc:mysql://localhost:3306/mysql?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
                config.adminJdbcUrl()
        );
        assertEquals("root", config.username());
        assertEquals("root", config.password());
        assertEquals("mesos", config.databaseName());
    }

    @Test
    void derivesAdminUrlPreservingJdbcQueryParameters() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of(
                "MESOS_RANKING_DB_URL",
                "jdbc:mysql://db.example:3307/game?connectionTimeZone=UTC&useSSL=false"
        ));

        assertEquals(
                "jdbc:mysql://db.example:3307/mysql?connectionTimeZone=UTC&useSSL=false",
                config.adminJdbcUrl()
        );
        assertEquals("game", config.databaseName());
    }

    @Test
    void disablesRankingDatabaseWithNewEnabledFlag() {
        ServerRuntimeConfig config = ServerRuntimeConfig.fromEnvironment(Map.of("MESOS_RANKING_DB_ENABLED", "false"));

        assertFalse(config.dbEnabled());
    }
}
