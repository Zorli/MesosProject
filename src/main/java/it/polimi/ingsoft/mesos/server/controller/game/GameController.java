package it.polimi.ingsoft.mesos.server.controller.game;


import it.polimi.ingsoft.mesos.server.controller.command.*;
import it.polimi.ingsoft.mesos.server.controller.game.enums.GameCommandType;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * Coordinates one running match from the perspective of external callers.
 *
 * <p>The orchestrator is responsible for owning the {@link Game} created for a lobby and keeping
 * the list of players participating in that match. Higher layers are expected to route all
 * game-specific requests through this class so that access checks and orchestration logic stay
 * centralized.
 */
public class GameController implements Runnable  {
    private static final Logger log = Logger.getLogger(GameController.class.getName());

    private final Game game;
    private final List<Player> players;
    private final String hostNickname;
    private final ServerSessionRegistry sessions;
    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

    //L'idea è di avere questo per evitare che magari mentre sta facendo la fase di teardown, il sesion registry continui
    //a permettere connessioni, inoltre prima di fare il teardown svuotiamo la coda dei messaggi che potrebbero essere arrivati
    //notificando che il game sta chiudendo e non risponderà più.
    private final Object closingLock = new Object();
    private boolean closing;

    // Constructors

    public GameController(Game game,
                          String hostNickname,
                          ServerSessionRegistry sessions) {
        Objects.requireNonNull(game, "Game cannot be null");
        if (game.getPlayers() == null || game.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        this.game = game;
        this.players = List.copyOf(game.getPlayers());
        this.hostNickname = requireHostNickname(hostNickname);
        this.sessions = sessions != null ? sessions : new ServerSessionRegistry();
        this.closing = false;
    }

    // Getters

    /**
     * Returns the game aggregate coordinated by this orchestrator.
     *
     * @return coordinated game
     */
    public Game getGame() {
        return this.game;
    }

    /**
     * Returns the players participating in the coordinated match.
     *
     * @return match players
     */
    public List<Player> getPlayers() {
        return this.players;
    }

    private void setClosing(boolean closing) {
        synchronized (this.closingLock) {
            this.closing = closing;
        }
    }

    public boolean isClosing() {
        synchronized (this.closingLock) {
            return this.closing;
        }
    }


    public void enqueueMessage(QueuedCommand command) {
        this.commandQueue.add(command);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                QueuedCommand cmd = this.commandQueue.take();
                if(cmd instanceof SystemSetupMatchCommand){
                    this.setupMatch();
                    continue;
                }

                String sessionId = cmd.getSessionId();
                if (cmd instanceof SystemDisconnectCommand){
                    this.disconnectedPlayer(sessionId);
                    continue;
                }else if(cmd instanceof SystemJoinCommand){
                    this.reConnectedPlayer(sessionId);
                    continue;
                }

                //Tutto quello che arriva qua è un client payload(spero:)
                ClientPayloadCommand clientCommand = (ClientPayloadCommand) cmd ;
                ClientPayload payload = clientCommand.getPayload();
                try {
                    switch (GameCommandType.fromPayload(payload)) {
                        case CHOOSE_CARD -> {
                            ChooseCardPayload p = (ChooseCardPayload) payload;
                            this.chooseCard(sessionId, p.cardId);
                        }
                        case MOVE_TOTEM -> {
                            MoveTotemPayload p = (MoveTotemPayload) payload;
                            this.moveTotem(sessionId, p.tile);
                        }
                        case EXTRA_DRAW -> {
                            ExtraDrawPayload p = (ExtraDrawPayload) payload;
                            this.extraDrawn(sessionId, p.cardId);
                        }

                    }
                }catch(IllegalArgumentException e){
                    this.reportPhaseError(sessionId);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    /**
     * Performs match setup.
     */
    public void setupMatch() {
        this.executeStateChange(this.game::setup);
        this.checkGameEndAndCleanup();
    }

    /**
     * Places a player's totem.
     *
     * @param sessionId of the acting player session
     * @param tile target offer-space code
     */
    private void moveTotem(String sessionId, String tile) {
        Player player = this.resolvePlayerBySession(sessionId);
        this.executeStateChange(
                () -> this.game.moveTotem(player, tile)
        );
        this.checkGameEndAndCleanup();
    }

    /**
     * Delegates one standard card draw.
     *
     * @param sessionId of the acting player session
     * @param cardId of the chosen card
     */
    private void chooseCard(String sessionId, int cardId) {
        Player player = this.resolvePlayerBySession(sessionId);
        this.executeStateChange(
                () -> this.game.chooseCard(player, cardId)
        );
        this.checkGameEndAndCleanup();
    }

    /**
     * Delegates one extra draw.
     *
     * @param sessionId of the acting player session
     * @param cardId chosen extra-draw card
     */
    private void extraDrawn(String sessionId, int cardId) {
        Player player = this.resolvePlayerBySession(sessionId);
        this.executeStateChange(
                () -> this.game.extraDrawn(player, cardId)
        );
        this.checkGameEndAndCleanup();
    }


    // Private methods

    private Player resolvePlayer(String nickname) {
        return this.players.stream()
                .filter(p -> p.getNickname().equals(nickname))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not in this match: " + nickname));
    }

    private Player resolvePlayerBySession(String sessionId) {
        return this.resolvePlayer(this.sessions.nicknameOf(sessionId));
    }

    private void disconnectedPlayer(String sessionId) {
        Player player = this.resolvePlayerBySession(sessionId);
        Listener playerListener = this.sessions.getListenerOf(sessionId);
        this.game.removeListener(playerListener);
        this.executeStateChange(
                () -> this.game.disconnectedPlayer(player)
        );


        this.checkGameEndAndCleanup();
        if (this.game.getStatus() == GameStatus.FINISHED) {
            return;
        }

        this.sessions.leaveGame(sessionId);

        // quando uno si riconnete ci pensa il servercontroller a pulire la mappa, quando uno si disconnette lo registra il gameController
        this.sessions.RegisterDisconnection(sessionId,this.game.getId());
        this.sessions.unregister(sessionId);
    }

    private void reConnectedPlayer(String sessionId) {
        //qui arriverà tutto pulito, il giocatore sarà già dentro con la VirtualView attaccata come Listener
        //Le operazioni di gestione del registry in fase di riconnessione le facciamo qua invece che nel server cotnroler cosi evitiamo
        //problemi del tipo:
        //Nella coda del game sto per processare che il gioco muore
        //nel frattempo il server controller mi recupera un altro player che sta rientrnado
        //killo il game
        //nullpointeexception del player in entrata
        //se invece faccio tutto da qua, al massimo il secondo messaggio di reconnect viene perso perchè muore nella coda
        this.sessions.RegisterReConnection(sessionId);
        Listener playerListener = this.sessions.getListenerOf(sessionId);
        this.game.addListener(playerListener);
        Player player = this.resolvePlayerBySession(sessionId);
        this.executeStateChange(
                () -> this.game.reconnectedPlayer(player)
        );
        this.checkGameEndAndCleanup();
    }



    /**
     * Applies a state-changing command only if the match has not been frozen.
     *
     * @param action command to execute
     */
    private void executeStateChange(Runnable action) {
        action.run();
    }

    /**
     * Finalizes completed matches by tearing down session routing.
     */
    private void checkGameEndAndCleanup() {

        if (this.game.getStatus() != GameStatus.FINISHED) {
            return;
        }
        //Metto che il game sta chiudendo cosi il registry prima di fare dispatch dei game può
        //verificare che stia chiudendo in tal caso non considerà piu dispatchabile quel game
        this.setClosing(true);

        log.info("Game " + this.game.getId() + " finished.");
        this.teardownGame();
        Thread.currentThread().interrupt();
    }

    private void teardownGame() {
        // qui dovrebbe essere ok, cioè se uno si disconnette non viene "letto"
        //Arrivato qui la coda non dovrebbe più poter essere accessibile dall'esterno,
        //dato che abbiamo messo il flag di closing, potrei comunque
        //avere dei messaggi ancora pending, svuoto la coda notificando che sono in fase di chiusura
        //Oppure semplicemnte notifico che sta chiudendo
//        this.game.notifyListeners();

        List<ServerSessionRegistry.SessionSnapshot> playersInGame =
                this.sessions.sessionsInGame(this.game.getId());

        for (ServerSessionRegistry.SessionSnapshot snapshot : playersInGame) {
            this.sessions.leaveGame(snapshot.sessionId());
        }
        this.sessions.removeGameOrchestrator(this.game.getId());
        //qui leviamo tutte le coppie nella mappe di disconnesioni con il gameId del gioco finito, d'ora in poi nessuno
        // si può riconettere o connettere (dato che il GameOrchestrator non c'è più)
        this.sessions.removeDisconnectedReference(this.game.getId());
    }

    // Helper methods

    private static String requireHostNickname(String hostNickname) {
        if (hostNickname == null || hostNickname.isBlank()) {
            throw new IllegalArgumentException("Match host nickname cannot be null or blank");
        }
        return hostNickname;
    }

    private void reportPhaseError(String sessionId) {
        try {
            this.game.reportPhaseError(this.sessions.getListenerOf(sessionId));
        } catch (RuntimeException ignored) {
        }
    }

}
