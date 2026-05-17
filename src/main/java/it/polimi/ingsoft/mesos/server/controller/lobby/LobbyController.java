package it.polimi.ingsoft.mesos.server.controller.lobby;

import it.polimi.ingsoft.mesos.server.controller.command.*;
import it.polimi.ingsoft.mesos.server.controller.server.enums.PreLobbyCommandType;
import it.polimi.ingsoft.mesos.server.factory.GameFactory;
import it.polimi.ingsoft.mesos.server.factory.LobbyPlayerFactory;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.lobby.enums.LobbyCommandType;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.JoinLobbyPayload;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Objects;

public class LobbyController implements Runnable {

    private final Lobby lobby;
    private final ServerSessionRegistry sessions;
    private final GameFactory gameFactory;
    private final LobbyPlayerFactory lobbyPlayerFactory;

    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

    public LobbyController(
            Lobby lobby,
            ServerSessionRegistry sessions,
            GameFactory gameFactory) {

        this.lobby = lobby;
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
    }

    public void enqueueMessage(QueuedCommand command) {
        this.commandQueue.add(command);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                QueuedCommand cmd = this.commandQueue.take();
                //Controllo se è payload interno o esterno
                String sessionId = cmd.getSessionId();
                if (cmd instanceof SystemJoinCommand ) {
                    this.joinUser(sessionId);
                    continue;
                }else if(cmd instanceof SystemDisconnectCommand){
                    this.disconnectUser(sessionId);
                    continue;
                }

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
                } catch(IllegalArgumentException e){
                    this.reportPhaseError(sessionId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private void joinUser(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        Listener clientView = (Listener) this.sessions.connectionOf(sessionId);

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
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
        }
    }

    private void disconnectUser(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        Listener leavingListener = (Listener) this.sessions.connectionOf(sessionId);

        if (this.lobby.removePlayer(nickname, leavingListener)) {
            this.lobby.notifyPlayerLeft(nickname, leavingListener);
        }
        if (this.shouldDisposeEmptyLobby()) {
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
            GameController orchestrator = this.startNewMatch();
            this.startMatchCountdown(orchestrator);
            Thread.currentThread().interrupt();
        }
    }

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

    private void startGameWorker(GameController orchestrator) {
        Thread gameWorker = new Thread(orchestrator, "Game-" + orchestrator.getGame().getId() + "-Worker");
        gameWorker.start();
    }

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

    private void completeLobbyTransition(GameController orchestrator) {
        this.lobby.clearListeners();
        this.sessions.registerGameOrchestrator(orchestrator.getGame().getId(), orchestrator);
        this.sessions.removeLobbyController(this.lobby.getId());
        this.sessions.removeLobbyModel(this.lobby.getId());
    }

    private void startMatchCountdown(GameController orchestrator) {
        new Thread(() -> {
            try {
                // Aspetta 5 secondi (5000 millisecondi)
                Thread.sleep(2000);

                // Poi avvia il setup se la partita è ancora in stato SETUP
                if (orchestrator.getGame().getStatus() == GameStatus.SETUP) {
                    orchestrator.enqueueMessage(new SystemSetupMatchCommand());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "match-countdown-" + this.lobby.getId()).start();
    }

    private void reportPhaseError(String sessionId) {
        try {
            Listener listener = this.sessions.getListenerOf(sessionId);
            this.lobby.reportPhaseError(listener);
        } catch (RuntimeException ignored) {
        }
    }
}
