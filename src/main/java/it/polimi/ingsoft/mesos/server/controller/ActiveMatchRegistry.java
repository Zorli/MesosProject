package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Registry that keeps the single {@link GameOrchestrator} associated with each active lobby.
 *
 * <p>The registry is owned by the server controller and guarantees that multiple sessions entering
 * the same started lobby observe the same match orchestrator instance.
 */
public final class ActiveMatchRegistry {
    private final ConcurrentMap<Integer, GameOrchestrator> activeMatches = new ConcurrentHashMap<>();

    /**
     * Returns the active match for the given lobby, creating it at most once when absent.
     *
     * @param lobbyId lobby identifier associated with the active match
     * @param creator factory used only when the match has not been created yet
     * @return shared match orchestrator for the lobby
     */
    public GameOrchestrator getOrCreate(int lobbyId, Supplier<GameOrchestrator> creator) {
        Objects.requireNonNull(creator, "creator cannot be null");
        return this.activeMatches.computeIfAbsent(lobbyId, ignoredLobbyId -> {
            GameOrchestrator orchestrator = creator.get();
            return Objects.requireNonNull(orchestrator, "creator returned null orchestrator");
        });
    }
}
