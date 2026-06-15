package it.polimi.ingsoft.mesos.server.controller.preLobby;

import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinLobbyCommand;
import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.command.QueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.preLobby.enums.PreLobbyCommandType;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.preLobby.PreLobbyModel;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central coordinator for the pre-lobby phase of the server.
 *
 * <p>Runs on its own worker thread, consuming a {@link BlockingQueue} of
 * {@link QueuedCommand} instances in order. It handles user authentication,
 * orchestrates the creation and joining of game lobbies, and, on a successful
 * authentication, re-queues a returning player to a game it had previously
 * disconnected from.
 */
public class PreLobbyController implements Runnable {

    /**
     * The registry storing all session details and mappings.
     */
    private final ServerSessionRegistry sessions;

    /**
     * The business model for the pre-lobby actions.
     */
    private final PreLobbyModel preLobbyModel;

    /**
     * The factory responsible for constructing game engine instances.
     */
    private final GameFactory gameFactory;

    /**
     * Thread-safe blocking queue holding commands to be processed sequentially.
     */
    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

    /**
     * Constructs a {@code PreLobbyController} and initializes its pre-lobby model.
     *
     * @param gameFactory the factory to build game instances
     * @param sessions    the registry containing all active session states
     */
    public PreLobbyController(GameFactory gameFactory,
                              ServerSessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");

        this.preLobbyModel = new PreLobbyModel(this.sessions);
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
     * Enqueues a command to be processed by this controller's run loop.
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
                    this.preLobbyModel.reportPhaseError(sessionId);
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
                            TotemColor totemColor = p.totemColor;
                            this.joinLobby(sessionId, p.lobbyId, totemColor);
                        }
                        case CREATE_LOBBY -> {
                            CreateLobbyPayload p = (CreateLobbyPayload) payload;
                            this.createLobby(sessionId, p.chosenCapacity, p.totemColor);
                        }
                        case LIST_LOBBIES -> this.listOpenLobbies(sessionId);
                    }
                } catch(IllegalArgumentException e){
                    this.preLobbyModel.reportPhaseError(sessionId);
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
        if (!this.preLobbyModel.authenticate(sessionId, nickname)) {
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
        this.preLobbyModel.listOpenLobbies(sessionId);
    }

    /**
     * Creates a new game lobby with the specified capacity and host totem color,
     * transitions the host session to the LOBBY phase, and starts a dedicated lobby worker thread.
     *
     * @param sessionId      the session ID of the client hosting the lobby
     * @param chosenCapacity the maximum player capacity of the lobby
     * @param totemColor     the totem color selected by the host while creating the lobby
     */
    private void createLobby(String sessionId, int chosenCapacity, TotemColor totemColor) {
        Lobby lobby = this.preLobbyModel.createLobby(sessionId, chosenCapacity, totemColor);
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
     * Joins an existing lobby after performing pre-lobby validations.
     *
     * <p>If the target lobby exists and the session is allowed to enter it, this method
     * enqueues a {@link SystemJoinLobbyCommand} to the lobby worker. The actual reservation
     * of the selected totem color is performed by the lobby model on that worker thread.
     *
     * @param sessionId the session ID of the client attempting to join
     * @param lobbyId   the ID of the target lobby
     * @param totemColor the totem color selected by the joining player
     */
    private void joinLobby(String sessionId, int lobbyId, TotemColor totemColor) {
        if (!this.preLobbyModel.joinLobbyValidation(sessionId, lobbyId)) {
            return;
        }
        LobbyController target = this.sessions.getLobbyController(lobbyId);
        if (target == null) {
            this.preLobbyModel.reportJoinError(sessionId, "Lobby controller not found: " + lobbyId);
            return;
        }
        target.enqueueMessage(new SystemJoinLobbyCommand(sessionId, totemColor));
    }


    /**
     * Reports a phase execution error to the client session.
     *
     * @param sessionId the session ID of the client
     */
    public void reportPhaseError(String sessionId) {
        this.preLobbyModel.reportPhaseError(sessionId);
    }
}
