/**
 * Runtime services for active matches.
 *
 * <p>This package sits above {@code GameOrchestrator}: it owns active-match registration,
 * per-match command serialization and lifecycle cleanup, while keeping the game domain and the
 * orchestrator free from transport-thread concerns.
 */
package it.polimi.ingsoft.mesos.server.controller.match;
