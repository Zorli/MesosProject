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
                switch (LobbyCommandType.fromPayload(cmd.payload())) {
                    case START_LOBBY -> this.startLobby(sessionId);
                    case EXIT_LOBBY -> this.exitLobby(sessionId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void exitLobby(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);

        // Il Model fa controlli, aggiorna i suoi dati e spara il Broadcast.
        // Se tutto va a buon fine (ritorna true), il Controller sposta l'utente nel Registry.
        if (this.lobby.removePlayer(nickname)) {
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
            new Thread(orchestrator, "game-orchestrator-" + this.lobby.getId()).start();
            // ...e cancelliamo noi stessi dal database, dato che il nostro lavoro qui è finito!
            this.sessions.removeLobbyController(this.lobby.getId());
            this.sessions.removeLobbyModel(this.lobby.getId());
            // 6. Thread classico per i 5 secondi di attesa (invariato)
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
