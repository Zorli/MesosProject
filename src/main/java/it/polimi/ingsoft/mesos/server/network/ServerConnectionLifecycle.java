package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.command.SystemDisconnectCommand;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.server.ServerModel;

import java.util.Objects;

/**
 * Handles the lifecycle of server-side client sessions.
 * <p>
 * This class centralizes the operations that are about the connection itself:
 * registering a new session, recording keep-alive replies, reacting to timeouts,
 * and starting the correct disconnection flow. It is used by the
 * {@link it.polimi.ingsoft.mesos.server.controller.server.ServerController} for
 * normal connection operations and by {@link PingPongRoutine} when a session no
 * longer answers to ping messages.
 * <p>
 * The lifecycle service does not own sockets and does not execute lobby or game
 * rules. It reads and updates connection state through {@link ServerSessionRegistry}.
 * When a disconnected session belongs to a lobby or a game, it delegates the
 * disconnection to the proper controller by enqueueing a
 * {@link SystemDisconnectCommand}. This keeps phase-specific cleanup inside the
 * controller that owns that phase.
 */
public class ServerConnectionLifecycle {
    private final ServerSessionRegistry sessions;

    /**
     * Creates a lifecycle service backed by the shared session registry and
     * pre-lobby server model.
     *
     * @param sessions registry containing session state, phase, controllers, and
     *                 network connections
     * @throws NullPointerException if {@code sessions} or {@code serverModel} is null
     */
    public ServerConnectionLifecycle(ServerSessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
    }

    /**
     * Registers a newly accepted client connection and returns its session id.
     * <p>
     * The connection is stored in the session registry. If the same object can
     * also receive server updates through {@link Listener}, it is registered as a
     * global listener so that pre-lobby errors and responses can be sent back to
     * the client.
     *
     * @param connection network connection associated with the client
     * @return unique session id assigned by the registry
     * @throws NullPointerException if {@code connection} is null
     */
    public String registerConnection(ClientConnection connection) {
        return this.sessions.registerConnection(connection);
    }

    /**
     * Records that a client session answered the keep-alive ping.
     * <p>
     * The method only updates the timestamp stored in the registry. Timeout
     * detection is performed later by {@link PingPongRoutine} using registry
     * snapshots.
     *
     * @param sessionId session that sent a pong response
     */
    public void recordPong(String sessionId) {
        this.sessions.recordPong(sessionId);
    }

    /**
     * Starts the disconnection flow for a session.
     * <p>
     * The global listener is removed first, because after this point the session
     * should no longer receive pre-lobby messages. The current phase is then read
     * from the registry. Game and lobby sessions are not removed directly here:
     * a {@link SystemDisconnectCommand} is enqueued to the controller that owns
     * that phase, so the phase-specific model can decide how to clean up the
     * player. Sessions outside lobby and game are unregistered immediately.
     *
     * @param sessionId session to disconnect
     */
    public void disconnectSession(String sessionId) {

        ServerSessionRegistry.SessionSnapshot snapshot;
        try {
            snapshot = this.sessions.snapshotOf(sessionId);
        } catch (RuntimeException ignored) {
            return;
        }

        if (snapshot.phase() == ServerSessionRegistry.Phase.GAME && snapshot.gameId() != null) {
            GameController orchestrator = this.sessions.getGameOrchestrator(snapshot.gameId());
            if (orchestrator != null) {
                orchestrator.enqueueMessage(new SystemDisconnectCommand(snapshot.sessionId()));
                return;
            }
        } else if (snapshot.phase() == ServerSessionRegistry.Phase.LOBBY && snapshot.lobbyId() != null) {
            LobbyController controller = this.sessions.getLobbyController(snapshot.lobbyId());
            if (controller != null) {
                controller.enqueueMessage(new SystemDisconnectCommand(snapshot.sessionId()));
                return;
            }
        }

        this.sessions.unregister(sessionId);
    }

    /**
     * Handles a keep-alive timeout detected by the ping-pong routine.
     * <p>
     * The method first asks the registered {@link ClientConnection} to close, then
     * reuses the normal disconnection flow. Exceptions are ignored because timeout
     * handling is already a recovery path and the session may have disappeared
     * while the routine was processing an old snapshot.
     *
     * @param sessionId timed-out session
     */
    public void handleConnectionTimeout(String sessionId) {
        try {
            this.sessions.connectionOf(sessionId).close();
            this.disconnectSession(sessionId);
        } catch (Exception ignored) {
        }
    }
}
