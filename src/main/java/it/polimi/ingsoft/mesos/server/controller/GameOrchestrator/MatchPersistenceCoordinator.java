package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;

import java.util.Objects;

/**
 * Match-scoped coordinator that translates domain checkpoints into persistence-service calls.
 *
 * <p>The coordinator is owned by exactly one {@link GameOrchestrator} instance and keeps the
 * persistence policy outside of the domain model.
 */
public final class MatchPersistenceCoordinator {
    private final SavedMatchService savedMatchService;
    private final String hostNickname;

    /**
     * Creates the coordinator bound to one running match.
     *
     * @param savedMatchService persistence service selected for the current runtime mode
     * @param hostNickname original host nickname associated with the match
     */
    public MatchPersistenceCoordinator(SavedMatchService savedMatchService, String hostNickname) {
        this.savedMatchService = Objects.requireNonNull(savedMatchService, "Saved-match service cannot be null");
        if (hostNickname == null || hostNickname.isBlank()) {
            throw new IllegalArgumentException("Host nickname cannot be null or blank");
        }
        this.hostNickname = hostNickname;
    }

    /**
     * Persists the initial match snapshot after setup succeeds.
     *
     * @param game current game aggregate
     */
    public void recordMatchInitialized(Game game) {
        this.savedMatchService.initializeMatch(game, this.hostNickname);
    }

    /**
     * Persists the start-of-turn checkpoint after the placement phase hands control to draws.
     *
     * @param game current game aggregate
     * @param previousRoundPhase round phase observed before the successful mutation
     */
    public void recordAfterMoveTotem(Game game, RoundPhase previousRoundPhase) {
        Objects.requireNonNull(game, "Game cannot be null");
        Objects.requireNonNull(previousRoundPhase, "Previous round phase cannot be null");

        RoundPhase currentRoundPhase = game.getTurnOrderTrack().getCurrentRoundPhase();
        if (previousRoundPhase != currentRoundPhase && currentRoundPhase == RoundPhase.PLAYER_DRAWS) {
            this.savedMatchService.saveTurnStarted(game, this.hostNickname);
        }
    }

    /**
     * Persists an explicit turn-started checkpoint for a freshly reset round.
     *
     * @param game current game aggregate
     */
    public void recordTurnStarted(Game game) {
        this.savedMatchService.saveTurnStarted(game, this.hostNickname);
    }

    /**
     * Persists an explicit turn-finished checkpoint.
     *
     * @param game current game aggregate
     */
    public void recordTurnFinished(Game game) {
        this.savedMatchService.saveTurnFinished(game, this.hostNickname);
    }

    /**
     * Persists an explicit era-changed checkpoint.
     *
     * @param game current game aggregate
     */
    public void recordEraChanged(Game game) {
        this.savedMatchService.saveEraChanged(game, this.hostNickname);
    }

    /**
     * Persists the final completed-match checkpoint.
     *
     * @param game current game aggregate
     */
    public void recordMatchCompleted(Game game) {
        this.savedMatchService.completeMatch(game, this.hostNickname);
    }

    /**
     * Returns whether the current coordinator is backed by real persistence.
     *
     * @return {@code true} when persistence is enabled
     */
    public boolean isPersistenceEnabled() {
        return this.savedMatchService.isPersistenceEnabled();
    }
}
