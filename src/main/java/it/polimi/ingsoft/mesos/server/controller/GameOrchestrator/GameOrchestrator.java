package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;


import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.command.ClientQueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.controller.command.SystemSetupMatchCommand;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Coordinates one running match from the perspective of external callers.
 *
 * <p>The orchestrator is responsible for owning the {@link Game} created for a lobby and keeping
 * the list of players participating in that match. Higher layers are expected to route all
 * game-specific requests through this class so that access checks and orchestration logic stay
 * centralized.
 */
public class GameOrchestrator implements Runnable  {
    private final Game game;
    private final List<Player> players;
    private final String hostNickname;
    private final ServerSessionRegistry sessions;
    private final RankingService rankingService;
    private Optional<RankingResult> finalRankingResult;
    private boolean rankingRecorded;
    private boolean frozen;
    private final BlockingQueue<EnqueuedMessage> commandQueue = new LinkedBlockingQueue<>();

// Constructors

    /**
     * Creates an orchestrator for a new match using the provided game factory and player list.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param sessions the server session registry
     */
    public GameOrchestrator(GameFactory gameFactory, ArrayList<Player> players, ServerSessionRegistry sessions) {
        this(
                gameFactory,
                players,
                resolveDefaultHostNickname(players),
                sessions
        );
    }

    /**
     * Creates an orchestrator for a new match.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param hostNickname original host nickname associated with the match
     * @param sessions the server session registry
     */
    public GameOrchestrator(GameFactory gameFactory,
                            ArrayList<Player> players,
                            String hostNickname,
                            ServerSessionRegistry sessions) {
        this(gameFactory, players, hostNickname, null, sessions);
    }

    /**
     * Creates an orchestrator for a new match and wires final ranking registration.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param hostNickname original host nickname associated with the match
     * @param rankingService ranking service used to register completed matches; nullable when disabled
     * @param sessions the server session registry
     */
    public GameOrchestrator(GameFactory gameFactory,
                            ArrayList<Player> players,
                            String hostNickname,
                            RankingService rankingService,
                            ServerSessionRegistry sessions) {
        Objects.requireNonNull(gameFactory, "Game factory cannot be null");
        Objects.requireNonNull(sessions, "Server session registry cannot be null");
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        this.game = gameFactory.createGame(players);
        this.players = List.copyOf(players);
        this.hostNickname = requireHostNickname(hostNickname);
        this.sessions = sessions;
        this.rankingService = rankingService;
        this.finalRankingResult = Optional.empty();
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

    /**
     * Returns the ranking result produced when the match was finalized.
     *
     * @return final ranking result, empty when ranking is disabled or the match is not finished
     */
    public Optional<RankingResult> getFinalRankingResult() {
        return this.finalRankingResult;
    }

    // Public gameplay API
    //idem alla lobby
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
                    switch (cmd.getCommand()) {
                        case ClientQueuedCommand clientCommand -> {
                            String nickname = sessions.nicknameOf(sessionId);
                            switch (GameCommandType.fromPayload(clientCommand.payload())) {
                                case CHOOSE_CARD -> {
                                    ChooseCardPayload p = (ChooseCardPayload) clientCommand.payload();
                                    this.chooseCard(nickname, p.cardId);
                                }
                                case EXTRA_DRAW -> {
                                    ExtraDrawPayload p = (ExtraDrawPayload) clientCommand.payload();
                                    this.extraDrawn(nickname, p.cardId);
                                }
                                case MOVE_TOTEM -> {
                                    MoveTotemPayload p = (MoveTotemPayload) clientCommand.payload();
                                    this.moveTotem(nickname, p.tile);
                                }
                            }
                        }
                        case SystemSetupMatchCommand ignored -> this.setupMatch();
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
    /**
     * Performs match setup.
     */
    private void setupMatch() {
        this.ensureMatchIsWritable();
        this.game.setup();
        this.checkGameEndAndCleanup();
    }

    /**
     * Places a player's totem.
     *
     * @param nickname of the acting player
     * @param tile target offer-space code
     */
    private void moveTotem(String nickname, String tile) {
        Player player = this.resolvePlayer(nickname);
        this.ensureMatchIsWritable();
        this.game.moveTotem(player, tile);
        this.checkGameEndAndCleanup();
    }

    /**
     * Delegates one standard card draw.
     *
     * @param nickname of acting player
     * @param cardId of the chosen card
     */
    private void chooseCard(String nickname, int cardId) {
        Player player = this.resolvePlayer(nickname);
        this.ensureMatchIsWritable();
        this.game.chooseCard(player, cardId);
        this.checkGameEndAndCleanup();
    }

    /**
     * Delegates one extra draw.
     *
     * @param nickname acting player
     * @param cardId chosen extra-draw card
     */
    private void extraDrawn(String nickname, int cardId) {
        Player player = this.resolvePlayer(nickname);
        this.ensureMatchIsWritable();
        this.game.extraDrawn(player, cardId);
        this.checkGameEndAndCleanup();
    }

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

    private void ensureMatchIsWritable() {
        if (this.frozen) {
            throw new MatchFrozenException();
        }
    }

    private void recordRankingIfFinished() {
        if (this.rankingService == null || this.rankingRecorded) {
            return;
        }

        try {
            RankingResult rankingResult = this.rankingService.recordMatchAndGetRanking(
                    String.valueOf(this.game.getId()),
                    this.players.size(),
                    Instant.now(),
                    this.hostNickname,
                    this.buildFinalScores()
            );
            this.finalRankingResult = Optional.of(rankingResult);
            this.rankingRecorded = true;
        } catch (RuntimeException exception) {
            this.frozen = true;
            throw new RankingFinalizationFailureException(exception);
        }
    }

    private List<MatchScore> buildFinalScores() {
        return this.players.stream()
                .map(player -> new MatchScore(player.getNickname(), player.getPrestigePoints()))
                .toList();
    }
    private void checkGameEndAndCleanup() {
        if (this.game.getStatus() != GameStatus.FINISHED) {
            return;
        }
        try{
            this.recordRankingIfFinished();
        }catch (RankingFinalizationFailureException e){
            //boh forse da aggiungere errore personalizzato su mancanza di dati registrati
        }
        finally {
            this.teardownGame();
            Thread.currentThread().interrupt();
        }
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

    private static String resolveDefaultHostNickname(List<Player> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        Player host = players.getFirst();
        if (host == null || host.getNickname() == null || host.getNickname().isBlank()) {
            throw new IllegalArgumentException("Match host nickname cannot be null or blank");
        }

        return requireHostNickname(host.getNickname());
    }

    private static String requireHostNickname(String hostNickname) {
        if (hostNickname == null || hostNickname.isBlank()) {
            throw new IllegalArgumentException("Match host nickname cannot be null or blank");
        }
        return hostNickname;
    }

    private void notifyPhaseError(String sessionId) {
        try {
            this.game.notifyActionError(this.sessions.getListenerOf(sessionId));
        } catch (RuntimeException ignored) {
        }
    }
}
