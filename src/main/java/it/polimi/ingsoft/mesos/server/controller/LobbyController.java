package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.LobbyPlayerFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.SystemJoinPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LobbyController implements Runnable {

    private final Lobby lobby;
    private final ServerSessionRegistry sessions;
    private final GameFactory gameFactory;
    private final RankingService rankingService;
    private final LobbyPlayerFactory lobbyPlayerFactory;
    private final PreMatchPayloadMapper payloadMapper;

    private final BlockingQueue<EnqueuedMessage> commandQueue = new LinkedBlockingQueue<>();

    public LobbyController(
            Lobby lobby,
            ServerSessionRegistry sessions,
            GameFactory gameFactory,
            RankingService rankingService) {

        this.lobby = lobby;
        this.sessions = sessions;
        this.gameFactory = gameFactory;
        this.rankingService = rankingService;
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
        this.payloadMapper = new PreMatchPayloadMapper();
    }

    public void enqueueMessage(EnqueuedMessage command) {
        this.commandQueue.add(command);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                EnqueuedMessage cmd = this.commandQueue.take();
                String sessionId = cmd.sessionId();
                switch(cmd.payload()){
                    case StartLobbyPayload p -> {this.startLobby(sessionId);}
                    case ExitLobbyPayload p -> {this.exitLobby(sessionId);}
                    case SystemJoinPayload p -> {this.joinUser(sessionId,p.nickname,p.clientView);}
                    // aggiustare unicast
                    default -> throw new IllegalStateException("Unexpected payload: " + cmd.payload());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private void joinUser(String sessionId, String nickname, Listener clientView) {
        // 1. Deleghiamo al Model (che ora gestisce tutto: logica, Unicast di errore e Broadcast)
        boolean joinedSuccessfully = this.lobby.addPlayer(nickname, clientView);

        // 2. Il Controller agisce sulla rete/macchina a stati GLOBALE
        // SOLO se il Model ha dato semaforo verde!
        if (joinedSuccessfully) {
            this.sessions.enterLobby(sessionId, this.lobby.getId());
        }

        // Se joinedSuccessfully è false, il Controller NON FA NULLA.
        // Il Model si è già occupato di inviare l'ErrorPayload (Unicast) al clientView.
    }
    public void exitLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);

        // Il Model fa controlli, aggiorna i suoi dati e spara il Broadcast.
        // Se tutto va a buon fine (ritorna true), il Controller sposta l'utente nel Registry.
        if (this.lobby.removePlayer(nickname,null)) {
            Listener listenerToRemove = (Listener) this.sessions.connectionOf(sessionId);
            this.lobby.removeListener(listenerToRemove);
            this.sessions.leaveLobby(sessionId);
        }
        if (this.lobby.isEmpty()) {
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
        }
    }

    public void startLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);

        // Il Model verifica host, numero giocatori, aggiorna lo stato a STARTED e spara il Broadcast.
        if (this.lobby.startMatch(nickname)) {

            // 1. Creiamo il GameOrchestrator (il Model del livello 3)
            List<Player> players = this.lobbyPlayerFactory.createPlayers(this.lobby.getPlayers());
            GameOrchestrator orchestrator = new GameOrchestrator(
                    this.gameFactory,
                    new ArrayList<>(players),
                    this.lobby.getHostNickname(),
                    this.rankingService,
                    this.sessions
            );

            // 2. RECUPERIAMO GLI UTENTI DA SPOSTARE
            List<ServerSessionRegistry.SessionSnapshot> lobbySessions = this.sessions.sessionsInLobby(this.lobby.getId());

            // 3. FSM SWITCH MASSIVO E PASSAGGIO DI CONSEGNE (IL TRUCCO È QUI)
            for (ServerSessionRegistry.SessionSnapshot session : lobbySessions) {
                // A) Spostiamo l'utente nella tabella di routing (Registry)
                this.sessions.enterGame(session.sessionId(), this.lobby.getId());

                // B) Recuperiamo il "cavo di rete" fisico
                Listener playerListener = (Listener) session.connection();

                // C) Lo attacchiamo al nuovo Motore di Gioco
                if (playerListener != null) {
                    playerListener.notifyUpdate(this.payloadMapper.lobbyStarted(session.user().getNickname(), this.lobby));
                    orchestrator.getGame().addListener(playerListener);
                    // (Nota: assumo che il tuo Game abbia il metodo addListener che mi hai mostrato nel codice precedente!)
                }
            }

            // 4. Stacchiamo tutti dalla vecchia Lobby, ormai diventata inutile
            this.lobby.clearListeners();
            this.sessions.registerGameOrchestrator(this.lobby.getId(), orchestrator);
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
            Thread gameWorker = new Thread(orchestrator, "Game-" + this.lobby.getId() + "-Worker");
            gameWorker.start();
            Thread.currentThread().interrupt();
            this.startMatchCountdown(orchestrator);
        }
    }

    private void startMatchCountdown(GameOrchestrator orchestrator) {
        new Thread(() -> {
            try {
                // Aspetta 5 secondi (5000 millisecondi)
                Thread.sleep(5000);

                // Poi avvia il setup se la partita è ancora in stato SETUP
                if (orchestrator.getGame().getStatus() == GameStatus.SETUP) {
                    orchestrator.setupMatch();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "match-countdown-" + this.lobby.getId()).start();
    }
}
