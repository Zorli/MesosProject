package it.polimi.ingsoft.mesos.server.controller.server;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.command.QueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.server.enums.PreLobbyCommandType;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.network.ServerConnectionLifecycle;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.server.ServerModel;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.PingPongRoutine;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The central coordinator for the pre-lobby phase of the server.
 * It manages a blocking command queue, processes user authentication,
 * orchestrates the creation and joining of game lobbies, and monitors
 * client connections using a dedicated ping-pong check routine.
 */
public class ServerController implements Runnable {

    /**
     * The registry storing all session details and mappings.
     */
    private final ServerSessionRegistry sessions;

    /**
     * The business model for the pre-lobby actions.
     */
    private final ServerModel serverModel;

    /**
     * The factory responsible for constructing game engine instances.
     */
    private final GameFactory gameFactory;

    /**
     * Thread-safe blocking queue holding commands to be processed sequentially.
     */
    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

    /**
     * Constructs a ServerController instance, initializes its sub-services,
     * and starts the network ping-pong timeout routine.
     *
     * @param gameFactory the factory to build game instances
     * @param sessions    the registry containing all active session states
     */
    public ServerController(GameFactory gameFactory,
                            ServerSessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");

        this.serverModel = new ServerModel(this.sessions);
    }

    /**
     * Retrieves the server session registry.
     *
     * @return the ServerSessionRegistry reference
     */
    public ServerSessionRegistry getSessionRegistry() {
        return this.sessions;
    }

    /**
     * Enqueues a command to be processed by the server controller run loop.
     *
     * @param command the QueuedCommand to add to the processing queue
     */
    public void enqueueMessage(QueuedCommand command) {
        this.commandQueue.add(command);
    }

    /**
     * Main execution loop running in a dedicated thread.
     * Dequeues commands, extracts payloads, and delegates their execution to internal handlers.
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                QueuedCommand cmd = this.commandQueue.take();
                String sessionId = cmd.getSessionId();

                if (!(cmd instanceof ClientPayloadCommand clientCommand)) {
                    this.serverModel.reportPhaseError(sessionId);
                    continue;
                }

                ClientPayload payload = clientCommand.getPayload();
                try {
                    switch (PreLobbyCommandType.fromPayload(payload)) {
                        case AUTHENTICATE -> {
                            AuthenticationPayload p = (AuthenticationPayload) payload;
                            this.authenticate(sessionId, p.nickname);
                        }
                        case JOIN_LOBBY -> {
                            JoinLobbyPayload p = (JoinLobbyPayload) payload;
                            this.joinLobby(sessionId, p.lobbyId);
                        }
                        case CREATE_LOBBY -> {
                            CreateLobbyPayload p = (CreateLobbyPayload) payload;
                            this.createLobby(sessionId, p.chosenCapacity);
                        }
                        case LIST_LOBBIES -> this.listOpenLobbies(sessionId);
                    }
                } catch(IllegalArgumentException e){
                    this.serverModel.reportPhaseError(sessionId);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Authenticates a user session with a chosen nickname.
     *
     * <p>If authentication succeeds and the nickname has a reconnect slot, the
     * session is queued to rejoin that game. If the old game is already closed,
     * the reconnect slot is removed.
     *
     * @param sessionId the unique session ID of the client
     * @param nickname  the chosen nickname
     */
    private void authenticate(String sessionId, String nickname) {
        if (!this.serverModel.authenticate(sessionId, nickname)) {
            return;
        }
        Integer gameId = this.sessions.reconnectGameIdOf(nickname);
        if(gameId != null)
        {
            GameController gameController = this.sessions.getGameOrchestrator(gameId);
            if(gameController == null || gameController.isClosing() ){
                this.sessions.removeReconnectSlot(nickname);
                return;
            }
            gameController.enqueueMessage(new SystemJoinCommand(sessionId));
        }
    }

    /**
     * Retrieves the list of currently open lobbies and notifies the requesting client.
     *
     * @param sessionId the session ID of the client requesting the list
     */
    private void listOpenLobbies(String sessionId) {
        this.serverModel.listOpenLobbies(sessionId);
    }

    /**
     * Creates a new game lobby with the specified capacity, transitions the host
     * session to the LOBBY phase, and starts a dedicated lobby worker thread.
     *
     * @param sessionId      the session ID of the client hosting the lobby
     * @param chosenCapacity the maximum player capacity of the lobby
     */
    private void createLobby(String sessionId, int chosenCapacity) {
        Lobby lobby = this.serverModel.createLobby(sessionId, chosenCapacity);
        if (lobby != null) {
            this.sessions.enterLobby(sessionId, lobby.getId());

            LobbyController lobbyController = new LobbyController(
                    lobby,
                    this.sessions,
                    this.gameFactory
            );
            this.sessions.registerLobbyController(lobby.getId(), lobbyController);
            Thread lobbyWorker = new Thread(lobbyController, "Lobby-" + lobby.getId() + "-Worker");
            lobbyWorker.start();
        }
    }

    /**
     * Joins an existing lobby after performing validations. If validation succeeds,
     * a system join command is enqueued to the target lobby controller.
     *
     * @param sessionId the session ID of the client attempting to join
     * @param lobbyId   the ID of the target lobby
     */
    private void joinLobby(String sessionId, int lobbyId) {
        if (!this.serverModel.joinLobbyValidation(sessionId, lobbyId)) {
            return;
        }
        LobbyController target = this.sessions.getLobbyController(lobbyId);
        if (target == null) {
            this.serverModel.reportJoinError(sessionId, "Lobby controller not found: " + lobbyId);
            return;
        }
        target.enqueueMessage(new SystemJoinCommand(sessionId));
    }

//    /**
//     * Registers a new client connection, assigning it a unique session ID.
//     *
//     * @param connection the ClientConnection representing the client
//     * @return the generated unique session ID
//     */
//    public String registerConnection(ClientConnection connection) {
//        return this.connectionLifecycle.registerConnection(connection);
//    }

//    /**
//     * Records a pong response received from the client to keep their session active.
//     *
//     * @param sessionId the session ID of the client responding to ping
//     */
//    public void recordPong(String sessionId) {
//        this.connectionLifecycle.recordPong(sessionId);
//    }

//    /**
//     * Disconnects a client session and cleans up its registry states and references.
//     *
//     * @param sessionId the session ID of the client being disconnected
//     */
//    public void disconnectSession(String sessionId) {
//        this.connectionLifecycle.disconnectSession(sessionId);
//    }

    /**
     * Reports a phase execution error to the client session.
     *
     * @param sessionId the session ID of the client
     */
    public void reportPhaseError(String sessionId) {
        this.serverModel.reportPhaseError(sessionId);
    }
}
