package it.polimi.ingsoft.mesos.server.model.ranking;

import java.util.List;

/**
 * PostgreSQL schema contribution used by ranking storage.
 */
interface PostgresSchemaModule {
    String moduleName();

    List<String> createStatements();

    List<String> requiredTables();
}
