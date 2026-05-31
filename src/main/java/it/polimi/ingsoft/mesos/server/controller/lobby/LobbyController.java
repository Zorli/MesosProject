package it.polimi.ingsoft.mesos.server.controller.lobby;

import it.polimi.ingsoft.mesos.server.controller.command.*;
import it.polimi.ingsoft.mesos.server.factory.GameFactory;
import it.polimi.ingsoft.mesos.server.factory.LobbyPlayerFactory;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.lobby.enums.LobbyCommandType;
import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Objects;

/**
 * Controller responsible for managing a single active {@link Lobby} instance and processing
 * all network/system commands relating to it.
 *
 *  <p><strong>Threading &amp; Design:</strong>
 * <ul>
 *   <li>Implements {@link Runnable} and executes inside its own dedicated worker thread (named {@code Lobby-ID-Worker}).</li>
 *   <li>Uses a threat-safe command queue ({@link BlockingQueue}) to receive commands asynchronously.</li>
 *   <li>The run loop processes enqueued commands sequentially (FIFO), preventing concurrency conflicts
 *       and race conditions on the underlying {@link Lobby} model without lock overhead.</li>
 * </ul>
 *
 * <p><strong>Lobby to Game Transition Flow:</strong>
 * When the host triggers the match start:
 * <ol>
 *   <li>Validates state via {@link Lobby#startMatch(String)}.</li>
 *   <li>Builds player instances using {@link LobbyPlayerFactory} (assigning totem colors and shuffling order).</li>
 *   <li>Instantiates a {@link Game} and a corresponding {@link GameController}.</li>
 *   <li>Moves connected sessions in {@link ServerSessionRegistry} from Lobby phase to Game phase.</li>
 *   <li>Migrates listeners from the Lobby observer list to the Game observer list.</li>
 *   <li>Unregisters itself from the registry and terminates its own run loop.</li>
 *   <li>Spins up a new {@code Game-ID-Worker} thread for the game.</li>
 *   <li>Spins up a temporary countdown thread to trigger match setup.</li>
 * </ol>
 */
public class LobbyController implements Runnable {

    /** The domain model representing the lobby state. */
    private final Lobby lobby;
    /** The global preLobby registry storing session states and controller routes. */
    private final ServerSessionRegistry sessions;
    /** The factory used to instantiate new {@link Game} matches. */
    private final GameFactory gameFactory;
    /** The factory used to map lobby users to game players. */
    private final LobbyPlayerFactory lobbyPlayerFactory;

    /** Command queue for receiving incoming user or system actions asynchronously. */
    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

    /**
     * Constructs a LobbyController for a specific lobby.
     *
     * @param lobby the {@link Lobby} model to manage
     * @param sessions the central {@link ServerSessionRegistry}
     * @param gameFactory the {@link GameFactory} used to boot the match
     */
    public LobbyController(
            Lobby lobby,
            ServerSessionRegistry sessions,
            GameFactory gameFactory) {

        this.lobby = lobby;
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
    }

    /**
     * Enqueues a command (e.g. user action, connection event, disconnection event) to be processed
     * sequentially by this controller's worker thread.
     *
     * @param command the command wrapper containing the payload and sender session details
     */
    public void enqueueMessage(QueuedCommand command) {
        this.commandQueue.add(command);
    }

    /**
     * Main run loop executing in a dedicated thread. Continuously takes commands from
     * the queue and dispatches them to appropriate handlers.
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                QueuedCommand cmd = this.commandQueue.take();
                String sessionId = cmd.getSessionId();

                // Dispatch system command notifications first
                if (cmd instanceof SystemJoinCommand) {
                    this.joinUser(sessionId);
                    continue;
                } else if (cmd instanceof SystemDisconnectCommand) {
                    this.disconnectUser(sessionId);
                    continue;
                }

                // Check that user command matches expected types
                if (!(cmd instanceof ClientPayloadCommand clientCommand)) {
                    this.reportPhaseError(sessionId);
                    continue;
                }

                ClientPayload payload = clientCommand.getPayload();
                try {
                    switch (LobbyCommandType.fromPayload(payload)) {
                        case EXIT_LOBBY -> this.exitLobby(sessionId);
                        case START_LOBBY -> this.startLobby(sessionId);
                        default -> throw new IllegalArgumentException("Unknown command type: " + payload.getClass());
                    }
                } catch (IllegalArgumentException e) {
                    this.reportPhaseError(sessionId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Handles adding an authenticated user connection to the lobby state.
     *
     * @param sessionId the session identifier of the client joining
     */
    private void joinUser(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        Listener clientView = (Listener) this.sessions.connectionOf(sessionId);
        // Delegate validation and model modifications to Lobby model
        boolean joinedSuccessfully = this.lobby.addPlayer(nickname, clientView);

        // Update central registry and notify all clients if join succeeded
        if (joinedSuccessfully) {
            this.sessions.enterLobby(sessionId, this.lobby.getId());
            this.lobby.notifyPlayerJoined(nickname);
        }
    }

    /**
     * Handles a user request to exit the lobby. Removes player from model, updates session state
     * back to PRE_LOBBY, and disposes of the lobby if it becomes empty.
     *
     * @param sessionId the session identifier of the client leaving
     */
    private void exitLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        Listener leavingListener = (Listener) this.sessions.connectionOf(sessionId);

        if (this.lobby.removePlayer(nickname, leavingListener)) {
            this.sessions.leaveLobby(sessionId);
            this.lobby.notifyPlayerLeft(nickname, leavingListener);
        }

        // Dispose empty lobbies
        if (this.shouldDisposeEmptyLobby()) {
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
        }
    }

    /**
     * Handles connection timeouts or connection failures. Removes the disconnected user from
     * the lobby model, disposes empty lobbies, and unregisters the session.
     *
     * @param sessionId the session identifier of the disconnected client
     */
    private void disconnectUser(String sessionId) {
        String nickname;
        Listener leavingListener;
        try {
            nickname = this.sessions.nicknameOf(sessionId);
            leavingListener = (Listener) this.sessions.connectionOf(sessionId);
        } catch (RuntimeException ignored) {
            return;
        }

        if (this.lobby.removePlayer(nickname, leavingListener)) {
            this.lobby.notifyPlayerLeft(nickname, leavingListener);
        }

        if (this.shouldDisposeEmptyLobby()) {
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
        }
        this.sessions.unregister(sessionId);
    }

    /**
     * Helper to verify if an empty lobby should be deleted.
     */
    private boolean shouldDisposeEmptyLobby() {
        return this.lobby.isEmpty();
    }

    /**
     * Handles the request to start the game match. If the model validates and successfully
     * starts, transitions the lobby into a game and shuts down this controller thread.
     *
     * @param sessionId the session identifier of the host initiating start
     */
    private void startLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);

        if (this.lobby.startMatch(nickname)) {
            GameController orchestrator = this.startNewMatch();
            this.startMatchCountdown(orchestrator);
            Thread.currentThread().interrupt(); // Stop LobbyController loop
        }
    }

    /**
     * Coordinates the creation of a new {@link Game} and a corresponding {@link GameController}.
     */
    private GameController startNewMatch() {
        Objects.requireNonNull(this.gameFactory, "gameFactory is null — cannot start a new match");
        List<Player> players = this.lobbyPlayerFactory.createPlayers(this.lobby.getPlayers());
        Game game = this.gameFactory.createGame(
                this.lobby.getId(),
                players
        );
        GameController orchestrator = new GameController(
                game,
                this.lobby.getHostNickname(),
                this.sessions
        );

        this.attachLobbySessionsToGame(orchestrator);
        this.completeLobbyTransition(orchestrator);
        this.startGameWorker(orchestrator);
        return orchestrator;
    }

    /**
     * Spins up the worker thread for the newly instantiated game match.
     */
    private void startGameWorker(GameController orchestrator) {
        Thread gameWorker = new Thread(orchestrator, "Game-" + orchestrator.getGame().getId() + "-Worker");
        gameWorker.start();
    }

    /**
     * Re-allocates active lobby client sessions into the game phase and moves
     * virtual view listeners to the Game model listener list.
     */
    private void attachLobbySessionsToGame(GameController orchestrator) {
        int gameId = orchestrator.getGame().getId();
        List<ServerSessionRegistry.SessionSnapshot> lobbySessions = this.sessions.sessionsInLobby(this.lobby.getId());

        for (ServerSessionRegistry.SessionSnapshot session : lobbySessions) {
            this.sessions.enterGame(session.sessionId(), gameId);

            Listener playerListener = (Listener) session.connection();
            if (playerListener != null) {
                this.lobby.notifyMatchStarted(session.user().getNickname(), playerListener);
                orchestrator.getGame().addListener(playerListener);
            }
        }
    }

    /**
     * Clears lobby references and registers the new GameController orchestrator.
     */
    private void completeLobbyTransition(GameController orchestrator) {
        this.lobby.clearListeners();
        this.sessions.registerGameOrchestrator(orchestrator.getGame().getId(), orchestrator);
        this.sessions.removeLobbyController(this.lobby.getId());
        this.sessions.removeLobbyModel(this.lobby.getId());
    }

    /**
     * Starts a short countdown timer to enqueue the initial setup phase message to the GameController.
     */
    private void startMatchCountdown(GameController orchestrator) {
        new Thread(() -> {
            try {
                // Wait for 2 seconds to allow client interfaces to transition
                Thread.sleep(2000);

                // Initialize game setup if the match is still in SETUP state
                if (orchestrator.getGame().getStatus() == GameStatus.SETUP) {
                    orchestrator.enqueueMessage(new SystemSetupMatchCommand());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "match-countdown-" + this.lobby.getId()).start();
    }

    /**
     * Helper to report an out-of-phase request directly to a client connection.
     */
    private void reportPhaseError(String sessionId) {
        try {
            Listener listener = this.sessions.getListenerOf(sessionId);
            this.lobby.reportPhaseError(listener);
        } catch (RuntimeException ignored) {
        }
    }
}
