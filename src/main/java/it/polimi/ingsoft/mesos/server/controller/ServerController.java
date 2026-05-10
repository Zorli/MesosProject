package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.command.ClientQueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.ServerModel;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.PingPongRoutine;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerController implements Runnable {

    private final ServerSessionRegistry sessions;
    private final ServerModel serverModel;
    private final GameFactory gameFactory;
    private final RankingService rankingService;
    private final MatchPersistence matchPersistence;
    private final ServerConnectionLifecycle connectionLifecycle;
    private final PingPongRoutine pingPongRoutine;


    // --- CODA PRE-LOBBY ---
    private final BlockingQueue<EnqueuedMessage> commandQueue = new LinkedBlockingQueue<>();

    public ServerController(GameFactory gameFactory,
                           RankingService rankingService,
                           MatchPersistence matchPersistence,
                           ServerSessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");
        this.rankingService = Objects.requireNonNull(rankingService, "rankingService cannot be null");
        this.matchPersistence = Objects.requireNonNull(matchPersistence, "matchPersistence cannot be null");

        this.serverModel = new ServerModel(this.sessions);

        this.connectionLifecycle = new ServerConnectionLifecycle(this.sessions, this.serverModel);
        this.pingPongRoutine = new PingPongRoutine(this.sessions, this.connectionLifecycle);
        this.pingPongRoutine.start();
    }

    public ServerSessionRegistry getSessionRegistry() {
        return this.sessions;
    }

    // --- GESTIONE DELLA CODA PRE-LOBBY ---

    public void enqueueMessage(EnqueuedMessage command) {
        this.commandQueue.add(command);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                EnqueuedMessage cmd = this.commandQueue.take();
                String sessionId = cmd.getSessionId();
                try {
                    if (!(cmd.getCommand() instanceof ClientQueuedCommand clientCommand)) {
                        this.serverModel.reportPhaseError(sessionId);
                        continue;
                    }
                    ClientPayload payload = clientCommand.payload();
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
                } catch (IllegalArgumentException e) {
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
                    this.gameFactory,
                    this.rankingService,
                    this.matchPersistence
            );
            this.sessions.registerLobbyController(lobby.getId(), lobbyController);
            Thread lobbyWorker = new Thread(lobbyController, "Lobby-" + lobby.getId() + "-Worker");
            lobbyWorker.start();
        }
    }

    private void joinLobby(String sessionId, int lobbyId) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        String nickname = this.sessions.nicknameOf(sessionId);
        if (!this.serverModel.joinLobbyValidation(sessionId, clientView,lobbyId)) {
            return;
        }
        LobbyController target = this.sessions.getLobbyController(lobbyId);
        if (target == null) {
            this.serverModel.reportJoinError(sessionId, "Lobby controller not found: " + lobbyId);
            return;
        }
        target.enqueueMessage(new EnqueuedMessage(sessionId, new SystemJoinCommand(nickname, clientView)));
    }

    public void reportRoutingError(String sessionId, String message) {
        this.serverModel.reportRoutingError(sessionId, message);
    }

    // --- LIFECYCLE DELLA CONNESSIONE ---

    public String registerConnection(ClientConnection connection) {
        return this.connectionLifecycle.registerConnection(connection);
    }

    public void recordPong(String sessionId) {
        this.connectionLifecycle.recordPong(sessionId);
    }

    public void disconnectSession(String sessionId) {
        this.connectionLifecycle.disconnectSession(sessionId);
    }
}
