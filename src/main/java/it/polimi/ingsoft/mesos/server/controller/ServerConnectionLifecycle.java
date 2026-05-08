package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.ServerModel;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;

import java.util.Objects;
//This class has now the duty to handle the lifecycle of the connection of the client to the server
//so now the logic is carried out completely from this class that has as caller the usual ping pong routine,
// but now we do not pass anymore through the server controller and we go directly to the server model
//From an inbound perspective (the vv) the servermodel is still a facade for this service
public class ServerConnectionLifecycle {
    private final ServerSessionRegistry sessions;
    private final ServerModel serverModel;

    public ServerConnectionLifecycle(ServerSessionRegistry sessions, ServerModel serverModel) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.serverModel = Objects.requireNonNull(serverModel, "serverModel cannot be null");
    }

    public String registerConnection(ClientConnection connection) {
        String sessionId = this.sessions.registerConnection(connection);
        if (connection instanceof Listener listener) {
            this.serverModel.addGlobalListener(sessionId, listener);
        }
        return sessionId;
    }

    public void recordPong(String sessionId) {
        this.sessions.recordPong(sessionId);
    }

    public void disconnectSession(String sessionId) {
        this.serverModel.removeGlobalListener(sessionId);

        ServerSessionRegistry.SessionSnapshot snapshot;
        try {
            snapshot = this.sessions.snapshotOf(sessionId);
        } catch (RuntimeException ignored) {
            return;
        }

        if (snapshot.phase() == ServerSessionRegistry.Phase.GAME && snapshot.gameId() != null) {
            GameOrchestrator orchestrator = this.sessions.getGameOrchestrator(snapshot.gameId());
            if (orchestrator != null) {
                orchestrator.freezeForDisconnectedPlayer(snapshot.user().getNickname());
            }
        } else if (snapshot.phase() == ServerSessionRegistry.Phase.LOBBY && snapshot.lobbyId() != null) {
            LobbyController controller = this.sessions.getLobbyController(snapshot.lobbyId());
            if (controller != null) {
                controller.exitLobby(sessionId);
            }
        }

        this.sessions.unregister(sessionId);
    }

    public void handleConnectionTimeout(String sessionId) {
        try {
            this.sessions.connectionOf(sessionId).close();
            this.disconnectSession(sessionId);
        } catch (Exception ignored) {
        }
    }
}
