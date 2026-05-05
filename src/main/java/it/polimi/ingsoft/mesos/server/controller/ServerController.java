package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.ServerModel;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.server.network.PingPongRoutine;
import it.polimi.ingsoft.mesos.shared.payload.client.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerController implements Runnable {

    private final ServerSessionRegistry sessions;
    private final ServerModel serverModel;
    private final GameFactory gameFactory;
    private final RankingService rankingService;
    private final PingPongRoutine pingPongRoutine;

    // --- LE "PAGINE GIALLE" DEI CONTROLLER ATTIVI ---
    //tutti i dati dentro
//    private final Map<Integer, LobbyController> activeLobbyControllers = new ConcurrentHashMap<>();
//    private final Map<Integer, GameOrchestrator> activeGames = new ConcurrentHashMap<>();

    // --- CODA PRE-LOBBY ---
    private final BlockingQueue<EnqueuedMessage> commandQueue = new LinkedBlockingQueue<>();

    public ServerController(GameFactory gameFactory, RankingService rankingService, ServerSessionRegistry sessions) {
        this.sessions = sessions;
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");
        this.rankingService = rankingService;

        // Il Model assume la piena responsabilità dei controlli di business
        this.serverModel = new ServerModel(this.sessions);

        this.pingPongRoutine = new PingPongRoutine(this.sessions, this::handleConnectionTimeout);
        this.pingPongRoutine.start();
    }
    //cacata
    public ServerController(ServerSessionRegistry sessions) {
        this(DefaultGameFactory.getInstance(), null, sessions);
    }
    public ServerController(GameFactory gameFactory, ServerSessionRegistry sessions) {
        this(gameFactory,null, sessions);
    }
    // --- YELLOW PAGES GETTERS (Usati dalla VirtualView per l'instradamento) ---

//    public LobbyController getLobbyController(int lobbyId) {
//        return this.activeLobbyControllers.get(lobbyId);
//    }
//
//    public GameOrchestrator getGameOrchestrator(int gameId) {
//        return this.activeGames.get(gameId);
//    }

    public ServerSessionRegistry getSessionRegistry() {
        return this.sessions;
    }

    public ServerModel getServerModel() {
        return this.serverModel;
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
                String sessionId = cmd.sessionId();
                switch (cmd.payload()){
                    case AuthenticationPayload p -> {this.authenticate(sessionId, p.nickname);}
                    case JoinLobbyPayload p -> {this.joinLobby(sessionId, p.lobbyId);}
                    case CreateLobbyPayload p -> {this.createLobby(sessionId, p.chosenCapacity);}
                    case ListLobbiesPayload p -> {this.listOpenLobbies(sessionId);}
                    default -> this.serverModel.reportPhaseError(sessionId,cmd.payload().toString());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // --- WRAPPER API PRE-LOBBY (Chiamati dai Command estratti dalla coda) ---

    public void authenticate(String sessionId, String nickname) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        this.serverModel.authenticate(sessionId, nickname,  clientView);
    }

    public void listOpenLobbies(String sessionId) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        this.serverModel.listOpenLobbies(sessionId, clientView);
    }

    public void createLobby(String sessionId, int chosenCapacity) {
        // 1. Chiamata al Model per logica ed eventuali errori
        Listener clientView = this.sessions.getListenerOf(sessionId);
        Lobby lobby = this.serverModel.createLobby(sessionId, chosenCapacity, clientView);

        // 2. Transizione e Setup Controller se successo
        if (lobby != null) {
            this.sessions.enterLobby(sessionId, lobby.getId());

            LobbyController lobbyController = new LobbyController(
                    lobby,
                    this.sessions,
                    this.gameFactory,
                    this.rankingService
            );
            this.sessions.registerLobbyController(lobby.getId(), lobbyController);
            Thread lobbyWorker = new Thread(lobbyController, "Lobby-" + lobby.getId() + "-Worker");
            lobbyWorker.start();
        }
    }

    public void joinLobby(String sessionId, int lobbyId) {
        Listener clientView = this.sessions.getListenerOf(sessionId);
        String nickname = this.sessions.nicknameOf(sessionId);
        try{
            this.serverModel.joinLobbyValidation(sessionId, clientView,lobbyId );
            LobbyController target = this.sessions.getLobbyController(lobbyId);
            target.enqueueMessage(new EnqueuedMessage(sessionId, new SystemJoinPayload(nickname, clientView)));
        }catch (Exception e) {
        }
    }

    // --- LIFECYCLE DELLA CONNESSIONE --- da spostare in altro luogo

    public String registerConnection(PingPongConnection connection) {
        return this.sessions.registerConnection(connection);
    }

    public void recordPong(String sessionId) {
        this.sessions.recordPong(sessionId);
    }

    public void disconnectSession(String sessionId) {
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
                // Nota: se la coda del LobbyController è thread-safe, possiamo chiamare exitLobby
                // in modo asincrono passandogli un comando. Se invece è chiamata sincrona, va bene così.
                controller.exitLobby(sessionId);
            }
        }

        this.sessions.unregister(sessionId);
    }

    private void handleConnectionTimeout(String sessionId) {
        try {
            this.sessions.connectionOf(sessionId).close();
            this.disconnectSession(sessionId);
        } catch (Exception ignored) {
        }
    }
}