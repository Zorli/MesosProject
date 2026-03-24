package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.PostgresSavedMatchSchemaModule;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class PostgresSavedMatchSchemaModuleTest extends PostgresIntegrationTestSupport {

    @Test
    void cleanupTriggerDeletesOnlyStaleInProgressMatches() throws Exception {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_cleanup", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        try (Connection connection = java.sql.DriverManager.getConnection(
                runtimeConfig.jdbcUrl(),
                runtimeConfig.username(),
                runtimeConfig.password()
        );
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO saved_matches(
                        game_id, host_nickname, player_count, status, current_era, game_status,
                        round_number, round_phase, turn_phase, current_player_index, last_checkpoint_type,
                        created_at, updated_at
                    )
                    VALUES
                    ('stale-in-progress', 'Alice', 2, 'IN_PROGRESS', 'first', 'IN_PROGRESS',
                     1, 'PLACING_TOTEMS', 'TURN_COMPLETED', 0, 'GAME_INITIALIZED',
                     CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours'),
                    ('stale-completed', 'Alice', 2, 'COMPLETED', 'first', 'FINISHED',
                     10, 'FINAL_SCORING', 'TURN_COMPLETED', 0, 'MATCH_COMPLETED',
                     CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '3 hours')
                    """);

            statement.execute("""
                    INSERT INTO saved_matches(
                        game_id, host_nickname, player_count, status, current_era, game_status,
                        round_number, round_phase, turn_phase, current_player_index, last_checkpoint_type
                    )
                    VALUES ('fresh-in-progress', 'Carol', 2, 'IN_PROGRESS', 'first', 'IN_PROGRESS',
                            1, 'PLACING_TOTEMS', 'TURN_COMPLETED', 0, 'GAME_INITIALIZED')
                    """);
        }

        try (Connection connection = java.sql.DriverManager.getConnection(
                runtimeConfig.jdbcUrl(),
                runtimeConfig.username(),
                runtimeConfig.password()
        );
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM saved_matches
                     WHERE game_id = ?
                     """)) {
            statement.setString(1, "stale-in-progress");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(0, resultSet.getInt(1));
            }

            statement.setString(1, "stale-completed");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }

            statement.setString(1, "fresh-in-progress");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }
        }
    }

    @Test
    void schemaModuleExposesExpectedSavedMatchTables() {
        assertIterableEquals(
                List.of(
                        "saved_matches",
                        "saved_match_players",
                        "saved_match_turn_order",
                        "saved_match_turn_choices",
                        "saved_match_offer_spaces",
                        "saved_match_starting_blocks",
                        "saved_match_board_cards",
                        "saved_match_player_cards",
                        "saved_match_player_card_state"
                ),
                PostgresSavedMatchSchemaModule.getInstance().requiredTables()
        );
    }

    @Test
    void bootstrapCreatesAllSavedMatchTables() throws Exception {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_tables", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        try (Connection connection = java.sql.DriverManager.getConnection(
                runtimeConfig.jdbcUrl(),
                runtimeConfig.username(),
                runtimeConfig.password()
        );
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = current_schema()
                     AND table_name = ANY (?)
                     ORDER BY table_name
                     """)) {
            statement.setArray(1, connection.createArrayOf("text", new String[]{
                    "saved_matches",
                    "saved_match_players",
                    "saved_match_turn_order",
                    "saved_match_turn_choices",
                    "saved_match_offer_spaces",
                    "saved_match_starting_blocks",
                    "saved_match_board_cards",
                    "saved_match_player_cards",
                    "saved_match_player_card_state"
            }));

            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.Set<String> tableNames = new java.util.LinkedHashSet<>();
                while (resultSet.next()) {
                    tableNames.add(resultSet.getString(1));
                }

                assertEquals(
                        Set.of(
                                "saved_matches",
                                "saved_match_players",
                                "saved_match_turn_order",
                                "saved_match_turn_choices",
                                "saved_match_offer_spaces",
                                "saved_match_starting_blocks",
                                "saved_match_board_cards",
                                "saved_match_player_cards",
                                "saved_match_player_card_state"
                        ),
                        tableNames
                );
            }
        }
    }
}
