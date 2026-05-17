package it.polimi.ingsoft.mesos.server.controller.server;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.command.QueuedCommand;
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

public class ServerController implements Runnable {

    private final ServerSessionRegistry sessions;
    private final ServerModel serverModel;
    private final GameFactory gameFactory;
    private final ServerConnectionLifecycle connectionLifecycle;
    private final PingPongRoutine pingPongRoutine;


    // --- CODA PRE-LOBBY ---
    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

    public ServerController(GameFactory gameFactory,
                            ServerSessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");

        this.serverModel = new ServerModel(this.sessions);

        this.connectionLifecycle = new ServerConnectionLifecycle(this.sessions, this.serverModel);
        this.pingPongRoutine = new PingPongRoutine(this.sessions, this.connectionLifecycle);
        this.pingPongRoutine.start();
    }

    public ServerSessionRegistry getSessionRegistry() {
        return this.sessions;
    }

    // --- GESTIONE DELLA CODA PRE-LOBBY ---

    public void enqueueMessage(QueuedCommand command) {
        this.commandQueue.add(command);
    }

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

    // --- WRAPPER API PRE-LOBBY (Chiamati dai Command estratti dalla coda) ---

    private void authenticate(String sessionId, String nickname) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        this.serverModel.authenticate(sessionId, nickname, clientView);
    }

    private void listOpenLobbies(String sessionId) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        this.serverModel.listOpenLobbies(sessionId, clientView);
    }

    private void createLobby(String sessionId, int chosenCapacity) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        Lobby lobby = this.serverModel.createLobby(sessionId, chosenCapacity, clientView);
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

    private void joinLobby(String sessionId, int lobbyId) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        if (!this.serverModel.joinLobbyValidation(sessionId, clientView,lobbyId)) {
            return;
        }
        LobbyController target = this.sessions.getLobbyController(lobbyId);
        if (target == null) {
            this.serverModel.reportJoinError(sessionId, "Lobby controller not found: " + lobbyId);
            return;
        }
        target.enqueueMessage(new SystemJoinCommand(sessionId));
    }


    // --- LIFECYCLE DELLA CONNESSIONE --- Non passano dalla coda perchè sono di controllo della connessione

    public String registerConnection(ClientConnection connection) {
        return this.connectionLifecycle.registerConnection(connection);
    }

    public void recordPong(String sessionId) {
        this.connectionLifecycle.recordPong(sessionId);
    }

    public void disconnectSession(String sessionId) {
        this.connectionLifecycle.disconnectSession(sessionId);
    }

    public void reportPhaseError(String sessionId) {
        this.serverModel.reportPhaseError(sessionId);
    }
}
