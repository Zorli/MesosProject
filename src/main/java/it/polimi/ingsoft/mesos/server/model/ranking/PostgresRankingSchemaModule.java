package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresSchemaModule;

import java.util.List;

/**
 * Modular PostgreSQL schema contribution for the ranking subsystem.
 */
public final class PostgresRankingSchemaModule implements PostgresSchemaModule {
    private static final PostgresRankingSchemaModule INSTANCE = new PostgresRankingSchemaModule();

    private PostgresRankingSchemaModule() {
    }

    /**
     * Returns the shared ranking schema module instance.
     *
     * @return singleton ranking schema module
     */
    public static PostgresRankingSchemaModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String moduleName() {
        return "ranking";
    }

    @Override
    public List<String> createStatements() {
        return List.of(
                PostgresRankingQueries.CREATE_USERS_TABLE,
                PostgresRankingQueries.CREATE_USER_RATINGS_TABLE,
                PostgresRankingQueries.CREATE_MATCH_RESULTS_TABLE,
                PostgresRankingQueries.CREATE_RATINGS_INDEX,
                PostgresRankingQueries.CREATE_MATCH_HISTORY_INDEX,
                PostgresRankingQueries.CREATE_MATCH_PLAYER_COUNT_INDEX
        );
    }

    @Override
    public List<String> requiredTables() {
        return List.of("users", "user_ratings", "match_results");
    }
}
