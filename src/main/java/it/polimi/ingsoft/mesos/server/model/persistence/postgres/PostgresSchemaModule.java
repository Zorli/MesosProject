package it.polimi.ingsoft.mesos.server.model.persistence.postgres;

import java.util.List;

/**
 * Defines one modular PostgreSQL schema contribution owned by a server subsystem.
 */
public interface PostgresSchemaModule {

    /**
     * Returns the human-readable module name used in diagnostics.
     *
     * @return schema module name
     */
    String moduleName();

    /**
     * Returns the DDL statements required to create or update this module schema.
     *
     * @return ordered DDL statements
     */
    List<String> createStatements();

    /**
     * Returns the tables that must exist for this module to be considered healthy.
     *
     * @return required table names
     */
    List<String> requiredTables();
}
