package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

/**
 * Routes deserialized client payloads to the controller that owns the current
 * session phase.
 * <p>
 * This class is the server-side dispatcher between the transport boundary and
 * the application controllers. TCP and RMI virtual views both produce the same
 * information after decoding a client request: the session id that sent the
 * request and a typed {@link ClientPayload}. The router uses the session id to
 * read the current phase from {@link ServerSessionRegistry}, then enqueues the
 * payload command to the controller responsible for that phase.
 * <p>
 * The router does not execute commands and does not validate whether a payload
 * is meaningful for a phase. Its responsibility is only architectural routing:
 * pre-lobby commands go to {@link ServerController}, lobby commands go to the
 * proper {@link LobbyController}, and game commands go to the proper
 * {@link GameController}. Phase-specific validation remains inside those
 * controllers.
 */
public class MessageRouter {
    private final ServerSessionRegistry registry;
    private final ServerController serverController;

    /**
     * Creates a router backed by the shared session registry and root server
     * controller.
     *
     * @param registry registry used to read the session phase and phase-specific
     *                 controller ids
     * @param serverController controller that owns pre-lobby commands and phase
     *                         error reporting
     */
    public MessageRouter(ServerSessionRegistry registry, ServerController serverController) {
        this.registry = registry;
        this.serverController = serverController;
    }

    /**
     * Routes one client payload according to the current phase of its session.
     * <p>
     * The method wraps the payload in a {@link ClientPayloadCommand}, preserving
     * the session id required by controllers to read the runtime state from the
     * registry. If the session is in a lobby or game phase but the corresponding
     * controller is no longer registered, the request cannot be routed safely and
     * a phase error is reported to the client.
     *
     * @param sessionId session that sent the payload
     * @param payload deserialized client-to-server payload
     * @throws RuntimeException if the session id is not registered in the session
     *                          registry
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
