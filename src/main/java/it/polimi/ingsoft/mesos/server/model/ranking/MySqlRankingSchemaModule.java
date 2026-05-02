package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.persistence.mysql.MySqlSchemaModule;

import java.util.List;

/**
 * Modular MySQL schema contribution for the ranking subsystem.
 */
public final class MySqlRankingSchemaModule implements MySqlSchemaModule {
    private static final MySqlRankingSchemaModule INSTANCE = new MySqlRankingSchemaModule();

    /**
     * Prevents external instantiation of the singleton schema module.
     */
    private MySqlRankingSchemaModule() {
    }

    /**
     * Returns the shared ranking schema module instance.
     *
     * @return singleton ranking schema module
     */
    public static MySqlRankingSchemaModule getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the diagnostics name of the ranking schema contribution.
     *
     * @return schema module name
     */
    @Override
    public String moduleName() {
        return "ranking";
    }

    /**
     * Returns the DDL statements required by the ranking subsystem.
     *
     * @return ordered schema-creation statements
     */
    @Override
    public List<String> createStatements() {
        return List.of(
                MySqlRankingQueries.CREATE_USERS_TABLE,
                MySqlRankingQueries.CREATE_USER_RATINGS_TABLE,
                MySqlRankingQueries.CREATE_MATCH_RESULTS_TABLE
        );
    }

    /**
     * Returns the tables that must exist for ranking persistence to be considered healthy.
     *
     * @return required ranking table names
     */
    @Override
    public List<String> requiredTables() {
        return List.of("users", "user_ratings", "match_results");
    }
}
