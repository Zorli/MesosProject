/**
 * Provides the ranking subsystem used to persist completed matches and compute global Elo data.
 *
 * <p>This package contains immutable ranking DTOs, input validation, Elo computation logic,
 * persistence support for PostgreSQL, and the service interface used by the rest of the server
 * model to register users, store results, and query leaderboard or match history data.
 */
package it.polimi.ingsoft.mesos.server.controller.ranking;
