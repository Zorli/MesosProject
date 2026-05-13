package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.LobbyPlayerFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.command.ClientQueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemLobbyDisconnectCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemSetupMatchCommand;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Objects;

public class LobbyController implements Runnable {

    private final Lobby lobby;
    private final ServerSessionRegistry sessions;
    private final GameFactory gameFactory;
    private final MatchPersistence matchPersistence;
    private final LobbyPlayerFactory lobbyPlayerFactory;
    private final GameOrchestrator restoredOrchestrator;

    private final BlockingQueue<EnqueuedMessage> commandQueue = new LinkedBlockingQueue<>();

    public LobbyController(
            Lobby lobby,
            ServerSessionRegistry sessions,
            GameFactory gameFactory) {

        this(lobby, sessions, gameFactory, MatchPersistence.disabled());
    }

    public LobbyController(
            Lobby lobby,
            ServerSessionRegistry sessions,
            GameFactory gameFactory,
            MatchPersistence matchPersistence) {

        this.lobby = lobby;
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");
        this.matchPersistence = Objects.requireNonNull(matchPersistence, "matchPersistence cannot be null");
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
        this.restoredOrchestrator = null;
    }

    public LobbyController(
            Lobby lobby,
            ServerSessionRegistry sessions,
            MatchPersistence matchPersistence,
            GameOrchestrator restoredOrchestrator) {

        this.lobby = Objects.requireNonNull(lobby, "lobby cannot be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = null;
        this.matchPersistence = Objects.requireNonNull(matchPersistence, "matchPersistence cannot be null");
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
        this.restoredOrchestrator = Objects.requireNonNull(restoredOrchestrator, "restoredOrchestrator cannot be null");
    }

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
                    switch(cmd.getCommand()){
                        case ClientQueuedCommand clientCommand -> {
                            switch(LobbyCommandType.fromPayload(clientCommand.payload())){
                                case START_LOBBY -> {this.startLobby(sessionId);}
                                case EXIT_LOBBY -> {this.exitLobby(sessionId);}
                            }
                        }
                        case SystemJoinCommand p -> {this.joinUser(sessionId,p.nickname,p.clientView);}
                        case SystemLobbyDisconnectCommand p -> {this.disconnectUser(sessionId, p.nickname, p.leavingListener);}
                        default -> throw new IllegalStateException("Unexpected command: " + cmd.getCommand());
                    }
                } catch (RuntimeException e) {
                    this.notifyPhaseError(sessionId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private void joinUser(String sessionId, String nickname, Listener clientView) {
        if (this.lobby.getStatus() == LobbyStatus.RESUMABLE && this.lobby.isExpectedResumePlayer(nickname)) {
            if (!this.lobby.rejoinResumablePlayer(nickname, clientView)) {
                return;
            }
            this.sessions.enterLobby(sessionId, this.lobby.getId());
            this.lobby.notifyPlayerJoined(nickname);
            return;
        }

        // 1. Deleghiamo al Model (che ora gestisce tutto: logica, Unicast di errore e Broadcast)
        boolean joinedSuccessfully = this.lobby.addPlayer(nickname, clientView);

        // 2. Il Controller agisce sulla rete/macchina a stati GLOBALE
        // SOLO se il Model ha dato semaforo verde!
        if (joinedSuccessfully) {
            this.sessions.enterLobby(sessionId, this.lobby.getId());
            this.lobby.notifyPlayerJoined(nickname);
        }

        // Se joinedSuccessfully è false, il Controller NON FA NULLA.
        // Il Model si è già occupato di inviare l'ErrorPayload (Unicast) al clientView.
    }
    private void exitLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);

        // Il Model fa controlli, aggiorna i suoi dati e spara il Broadcast.
        // Se tutto va a buon fine (ritorna true), il Controller sposta l'utente nel Registry.
        Listener leavingListener = (Listener) this.sessions.connectionOf(sessionId);
        if (this.lobby.removePlayer(nickname, leavingListener)) {
            this.sessions.leaveLobby(sessionId);
            this.lobby.notifyPlayerLeft(nickname, leavingListener);
        }
        if (this.shouldDisposeEmptyLobby()) {
            this.matchPersistence.delete(this.lobby.getId());
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
        }
    }

    private void disconnectUser(String sessionId, String nickname, Listener leavingListener) {
        if (this.lobby.removePlayer(nickname, leavingListener)) {
            this.lobby.notifyPlayerLeft(nickname, leavingListener);
        }
        if (this.shouldDisposeEmptyLobby()) {
            this.matchPersistence.delete(this.lobby.getId());
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
        }
        this.sessions.unregister(sessionId);
    }

    private boolean shouldDisposeEmptyLobby() {
        return this.lobby.isEmpty() && this.lobby.getStatus() != LobbyStatus.RESUMABLE;
    }

    private void startLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);

        if (this.lobby.startMatch(nickname)) {
            if (this.restoredOrchestrator != null) {
                this.resumeRestoredMatch();
            } else {
                GameOrchestrator orchestrator = this.startNewMatch();
                this.startMatchCountdown(orchestrator);
            }
            Thread.currentThread().interrupt();
        }
    }

    private GameOrchestrator startNewMatch() {
        Objects.requireNonNull(this.gameFactory, "gameFactory is null — cannot start a new match");
        List<Player> players = this.lobbyPlayerFactory.createPlayers(this.lobby.getPlayers());
        Game game = this.gameFactory.createGame(
                this.lobby.getId(),
                players
        );
        GameOrchestrator orchestrator = new GameOrchestrator(
                game,
                this.lobby.getHostNickname(),
                this.matchPersistence,
                this.sessions
        );

        this.attachLobbySessionsToGame(orchestrator, false);
        this.completeLobbyTransition(orchestrator);
        this.startGameWorker(orchestrator);
        return orchestrator;
    }

    private void resumeRestoredMatch() {
        GameOrchestrator orchestrator = this.restoredOrchestrator;
        this.attachLobbySessionsToGame(orchestrator, true);
        this.completeLobbyTransition(orchestrator);
        this.startGameWorker(orchestrator);
    }

    private void startGameWorker(GameOrchestrator orchestrator) {
        Thread gameWorker = new Thread(orchestrator, "Game-" + orchestrator.getGame().getId() + "-Worker");
        gameWorker.start();
    }

    private void attachLobbySessionsToGame(GameOrchestrator orchestrator, boolean sendCurrentState) {
        int gameId = orchestrator.getGame().getId();
        List<ServerSessionRegistry.SessionSnapshot> lobbySessions = this.sessions.sessionsInLobby(this.lobby.getId());

        for (ServerSessionRegistry.SessionSnapshot session : lobbySessions) {
            this.sessions.enterGame(session.sessionId(), gameId);

            Listener playerListener = (Listener) session.connection();
            if (playerListener != null) {
                this.lobby.notifyMatchStarted(session.user().getNickname(), playerListener);
                orchestrator.getGame().addListener(playerListener);
                if (sendCurrentState) {
                    playerListener.notifyUpdate(orchestrator.getGame().buildPayload());
                }
            }
        }
    }

    private void completeLobbyTransition(GameOrchestrator orchestrator) {
        this.lobby.clearListeners();
        this.sessions.registerGameOrchestrator(orchestrator.getGame().getId(), orchestrator);
        this.sessions.removeLobbyController(this.lobby.getId());
        this.sessions.removeLobbyModel(this.lobby.getId());
    }

    private void startMatchCountdown(GameOrchestrator orchestrator) {
        new Thread(() -> {
            try {
                // Aspetta 5 secondi (5000 millisecondi)
                Thread.sleep(2000);

                // Poi avvia il setup se la partita è ancora in stato SETUP
                if (orchestrator.getGame().getStatus() == GameStatus.SETUP) {
                    orchestrator.enqueueMessage(new EnqueuedMessage(null, new SystemSetupMatchCommand()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "match-countdown-" + this.lobby.getId()).start();
    }

    private void notifyPhaseError(String sessionId) {
        try {
            Listener listener = this.sessions.getListenerOf(sessionId);
            this.lobby.notifyActionError(listener);
        } catch (RuntimeException ignored) {
        }
    }
}
