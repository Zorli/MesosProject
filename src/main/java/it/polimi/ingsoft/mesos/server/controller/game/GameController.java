package it.polimi.ingsoft.mesos.server.controller.game;


import it.polimi.ingsoft.mesos.server.controller.command.*;
import it.polimi.ingsoft.mesos.server.controller.game.enums.GameCommandType;
import it.polimi.ingsoft.mesos.server.controller.game.exceptions.MatchFrozenException;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
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
    private boolean frozen;
    private final BlockingQueue<QueuedCommand> commandQueue = new LinkedBlockingQueue<>();

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

    /**
     * Returns whether the match has been frozen.
     *
     * @return {@code true} when gameplay commands are no longer accepted
     */
    public boolean isFrozen() {
        return this.frozen;
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

                if (!(cmd instanceof ClientPayloadCommand clientCommand)) {
                    this.reportPhaseError(sessionId);
                    continue;
                }

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

    /**
     * Freezes match flow due to disconnected player, preventing further state transitions.
     *
     * @param nickname disconnected player nickname
     */
    public void freezeForDisconnectedPlayer(String nickname) {
        this.frozen = true;
        this.game.notifyError("PLAYER_DISCONNECTED", "Player disconnected: " + nickname);
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
        this.executeStateChange(
                () -> this.game.disconnectedPlayer(player)
        );
        this.checkGameEndAndCleanup();
        this.sessions.unregister(sessionId);
    }

    private void reConnectedPlayer(String sessionId) {
        Player player = this.resolvePlayerBySession(sessionId);
        this.executeStateChange(
                () -> this.game.reconnectedPlayer(player)
        );
        this.checkGameEndAndCleanup();
    }

    private void ensureMatchIsNotFrozen() {
        if (this.frozen) {
            throw new MatchFrozenException();
        }
    }

    /**
     * Applies a state-changing command only if the match has not been frozen.
     *
     * @param action command to execute
     */
    private void executeStateChange(Runnable action) {
        this.ensureMatchIsNotFrozen();
        action.run();
    }

    /**
     * Finalizes completed matches by tearing down session routing.
     */
    private void checkGameEndAndCleanup() {
        if (this.frozen) {
            return;
        }

        if (this.game.getStatus() != GameStatus.FINISHED) {
            return;
        }

        log.info("Game " + this.game.getId() + " finished.");
        this.teardownGame();
        Thread.currentThread().interrupt();
    }

    private void teardownGame() {
        List<ServerSessionRegistry.SessionSnapshot> playersInGame =
                this.sessions.sessionsInGame(this.game.getId());

        for (ServerSessionRegistry.SessionSnapshot snapshot : playersInGame) {
            this.sessions.leaveGame(snapshot.sessionId());
        }
        this.sessions.removeGameOrchestrator(this.game.getId());
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
