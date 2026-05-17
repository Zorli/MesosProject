package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.GameClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.LobbyClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.PreLobbyClientPayload;

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
                LobbyController lobbyController = this.registry.getLobbyController(snapshot.lobbyId());
                lobbyController.enqueueMessage(new EnqueuedMessage(sessionId, payload));
            }
            case GAME -> {
                GameController gameOrchestrator = this.registry.getGameOrchestrator(snapshot.gameId());
                gameOrchestrator.enqueueMessage(new EnqueuedMessage(sessionId,payload));
            }
        }
    }
}
