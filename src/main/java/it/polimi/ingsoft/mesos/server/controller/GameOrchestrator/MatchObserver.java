package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

/**
 * Observer contract for one active match coordinated by a {@link GameOrchestrator}.
 *
 * <p>Implementations are typically transport adapters that react to successful gameplay
 * mutations, pull the latest {@code GamePayload} from the orchestrator, and relay it to the
 * connected client.
 */
public interface MatchObserver {

    /**
     * Reacts to a successful match mutation that changed the current snapshot.
     */
    void onMatchUpdated();
}
