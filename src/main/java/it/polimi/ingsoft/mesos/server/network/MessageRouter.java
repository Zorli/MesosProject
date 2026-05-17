package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
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
     Unica roba ho rimesso il null check, non so se sia possibile che siano null, probabnilmente se non facciamo porcate nel
     registry dovrebbe essere sicuro pero per ora lo teniamo, poi se siamo 100% sicuri che non può succedere che i controller siano null
     leviamo check e report dell'errore

     */
    public void routeMessage(String sessionId, ClientPayload payload) {
        ServerSessionRegistry.SessionSnapshot snapshot = this.registry.snapshotOf(sessionId);

        switch (snapshot.phase()) {
            case PRE_LOBBY -> {
                this.serverController.enqueueMessage(new ClientPayloadCommand(sessionId, payload));
            }
            case LOBBY -> {
                LobbyController lobbyController = this.registry.getLobbyController(snapshot.lobbyId());
                if (lobbyController == null) {
                    this.serverController.reportPhaseError(sessionId);
                    return;
                }
                lobbyController.enqueueMessage(new ClientPayloadCommand(sessionId, payload));
            }
            case GAME -> {
                GameController gameOrchestrator = this.registry.getGameOrchestrator(snapshot.gameId());
                if (gameOrchestrator == null) {
                    this.serverController.reportPhaseError(sessionId);
                    return;
                }
                gameOrchestrator.enqueueMessage(new ClientPayloadCommand(sessionId,payload));
            }
        }
    }
}
