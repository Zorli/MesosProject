package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

public class MessageRouter {
    private final ServerSessionRegistry registry;
    private final ServerController serverController;

    public MessageRouter(ServerSessionRegistry registry, ServerController serverController) {
        this.registry = registry;
        this.serverController = serverController;
    }

    /**
     * Unico punto di ingresso per tutti i payload inviati dai client.
     */
    public void routeMessage(String sessionId, ClientPayload payload) {
        ServerSessionRegistry.SessionSnapshot snapshot = this.registry.snapshotOf(sessionId);

        switch (snapshot.phase()) {
            case PRE_LOBBY -> {
                this.serverController.enqueueMessage(new EnqueuedMessage(sessionId, payload));
            }
            case LOBBY -> {
                // Recupera il controller specifico e inietta il messaggio direttamente a lui
                LobbyController lobbyController = this.registry.getLobbyController(snapshot.lobbyId());
                if (lobbyController != null) {
                    lobbyController.enqueueMessage(new EnqueuedMessage(sessionId, payload));
                } else {
                    this.serverController.reportRoutingError(sessionId, "Lobby non trovata per instradamento: " + snapshot.lobbyId());
                }
            }
            case GAME -> {
                GameOrchestrator gameOrchestrator = this.registry.getGameOrchestrator(snapshot.gameId());
                if (gameOrchestrator != null) {
                    gameOrchestrator.enqueueMessage(new EnqueuedMessage(sessionId, payload));
                } else {
                    this.serverController.reportRoutingError(sessionId, "Partita non trovata per instradamento: " + snapshot.gameId());
                }
            }
        }
    }
}
