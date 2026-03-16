/**
 * Provides the orchestration layer that exposes model operations to higher-level server
 * components.
 *
 * <p>Classes in this package coordinate the interaction between lobbies, running games, and the
 * transport layer. They receive validated client requests, invoke the appropriate domain actions,
 * and keep track of the latest action result returned by the model.
 */
package it.polimi.ingsoft.mesos.server.model.Orchestration;
