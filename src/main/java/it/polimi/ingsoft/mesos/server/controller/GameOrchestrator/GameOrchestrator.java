package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;


import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.command.ClientQueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.controller.command.SystemSetupMatchCommand;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.mapper.GameSnapshotMapper;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates one running match from the perspective of external callers.
 *
 * <p>The orchestrator is responsible for owning the {@link Game} created for a lobby and keeping
 * the list of players participating in that match. Higher layers are expected to route all
 * game-specific requests through this class so that access checks and orchestration logic stay
 * centralized.
 */
public class GameOrchestrator implements Runnable  {
    private static final Logger log = Logger.getLogger(GameOrchestrator.class.getName());
    private static final String CHECKPOINT_ERROR_CODE = "CHECKPOINT_ERROR";

    private final Game game;
    private final List<Player> players;
    private final String hostNickname;
    private final ServerSessionRegistry sessions;
    private final RankingService rankingService;
    private final MatchPersistence matchPersistence;
    private final GameSnapshotMapper gameSnapshotMapper;
    private Optional<RankingResult> finalRankingResult;
    private boolean rankingRecorded;
    private boolean frozen;
    private final BlockingQueue<EnqueuedMessage> commandQueue = new LinkedBlockingQueue<>();

    // Constructors

    public GameOrchestrator(Game game,
                            String hostNickname,
                            RankingService rankingService,
                            MatchPersistence matchPersistence,
                            ServerSessionRegistry sessions) {
        Objects.requireNonNull(game, "Game cannot be null");
        if (game.getPlayers() == null || game.getPlayers().isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        this.game = game;
        this.players = List.copyOf(game.getPlayers());
        this.hostNickname = requireHostNickname(hostNickname);
        this.sessions = sessions != null ? sessions : new ServerSessionRegistry();
        this.rankingService = rankingService != null ? rankingService : NoOpRankingService.INSTANCE;
        this.matchPersistence = matchPersistence != null ? matchPersistence : MatchPersistence.disabled();
        this.gameSnapshotMapper = new GameSnapshotMapper();
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
    public void setupMatch() {
        this.persistAfterStateChange("setupMatch", this.game::setup);
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
        this.persistAfterStateChange(
                "moveTotem",
                () -> this.game.moveTotem(player, tile)
        );
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
        this.persistAfterStateChange(
                "chooseCard",
                () -> this.game.chooseCard(player, cardId)
        );
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
        this.persistAfterStateChange(
                "extraDrawn",
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

    private void ensureMatchIsWritable() {
        if (this.frozen) {
            throw new MatchFrozenException();
        }
    }

    private void recordRankingIfFinished() {
        if (this.rankingRecorded) {
            return;
        }
        if (!this.rankingService.isRankingEnabled()) {
            this.rankingRecorded = true;
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

    /**
     * Applies a state-changing command and persists the new checkpoint only if
     * the game version changed.
     *
     * <p>Persistence is intentionally skipped when there is no observable state change so
     * that repeated illegal transitions are not checkpointed.
     *
     * @param eventName event label used in checkpoint failure diagnostics
     * @param action command to execute
     */
    private void persistAfterStateChange(String eventName, Runnable action) {
        this.ensureMatchIsWritable();

        long previousVersion = this.game.getStateVersion();
        action.run();

        if (this.game.getStateVersion() == previousVersion) {
            return;
        }

        this.persistCurrentStateAfterStateChange(eventName);
    }

    private void persistCurrentStateAfterStateChange(String eventName) {
        log.info("[Persistence] Triggering checkpoint after '" + eventName + "' (game=" + this.game.getId()
                + ", version=" + this.game.getStateVersion() + ")");
        try {
            this.matchPersistence.save(this.snapshotCurrentState());
        } catch (RuntimeException exception) {
            log.log(Level.SEVERE, "[Persistence] Checkpoint FAILED after '" + eventName + "' (game=" + this.game.getId() + ")", exception);
            this.freezeAfterPersistenceFailure(eventName, exception);
            throw new IllegalStateException("Unable to persist match after " + eventName, exception);
        }
    }

    private SavedMatchSnapshot snapshotCurrentState() {
        return this.gameSnapshotMapper.toSnapshot(this.game, this.hostNickname);
    }

    private void freezeAfterPersistenceFailure(String eventName, RuntimeException cause) {
        this.frozen = true;
        this.game.notifyError(
                CHECKPOINT_ERROR_CODE,
                "Match frozen: unable to save state after " + eventName + " - " + cause.getMessage()
        );
    }

    /**
     * Finalizes completed matches: records ranking, deletes checkpoint and tears down session routing.
     *
     * <p>If ranking finalization fails the match is frozen, the checkpoint is kept and no further
     * teardown is performed.
     */
    private void checkGameEndAndCleanup() {
        if (this.frozen) {
            return;
        }

        if (this.game.getStatus() != GameStatus.FINISHED) {
            return;
        }
        try {
            this.recordRankingIfFinished();
        } catch (RankingFinalizationFailureException ex) {
            throw ex;
        }

        if (this.frozen) {
            return;
        }

        log.info("[Persistence] Game " + this.game.getId() + " finished — deleting checkpoint.");
        try {
            this.matchPersistence.delete(this.game.getId());
        } catch (RuntimeException ignored) {
            // best-effort: a failed checkpoint delete must not block session teardown
        }

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

    private void notifyPhaseError(String sessionId) {
        try {
            this.game.notifyActionError(this.sessions.getListenerOf(sessionId));
        } catch (RuntimeException ignored) {
        }
    }

}
