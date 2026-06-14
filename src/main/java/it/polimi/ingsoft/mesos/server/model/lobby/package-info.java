/**
 * Provides the pre-game lobby model used to collect players before a match starts.
 *
 * <p>The classes in this package track lobby membership, host ownership, capacity, and lifecycle
 * status transitions (waiting, ready, started, closed). Creating the actual game orchestrator when a
 * match starts is the responsibility of the lobby controller, not of this model.
 */
package it.polimi.ingsoft.mesos.server.model.lobby;
