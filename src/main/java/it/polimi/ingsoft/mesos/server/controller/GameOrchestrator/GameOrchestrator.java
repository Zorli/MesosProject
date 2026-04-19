package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;


import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnStatePayload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Coordinates one running match from the perspective of external callers.
 *
 * <p>The orchestrator is responsible for owning the {@link Game} created for a lobby and keeping
 * the list of players participating in that match. Higher layers are expected to route all
 * game-specific requests through this class so that access checks and orchestration logic stay
 * centralized.
 *
 * <p>All durable gameplay transitions must flow through this class so checkpoint timing remains in
 * the application layer rather than leaking into callers or domain objects.
 */
public class GameOrchestrator {
    private final Game game;
    private final List<Player> players;
    private final String hostNickname;
    private final RankingService rankingService;
    private final MatchPersistenceCoordinator persistenceCoordinator;
    private final Set<MatchObserver> observers = new LinkedHashSet<>();
    private long cachedPayloadVersion = -1L;
    private GamePayload cachedPayload;
    private boolean frozen;

    /**
     * Creates an orchestrator for a new match using the provided game factory and player list.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     */
    public GameOrchestrator(GameFactory gameFactory, ArrayList<Player> players) {
        this(
                gameFactory,
                players,
                resolveDefaultHostNickname(players),
                NoOpSavedMatchService.INSTANCE
        );
    }

    /**
     * Creates an orchestrator for a new match and wires the per-match persistence coordinator.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param hostNickname original host nickname associated with the match
     * @param savedMatchService persistence service selected for the current runtime mode
     */
    public GameOrchestrator(GameFactory gameFactory,
                            ArrayList<Player> players,
                            String hostNickname,
                            SavedMatchService savedMatchService) {
        this(gameFactory, players, hostNickname, savedMatchService, null);
    }

    /**
     * Creates an orchestrator for a new match and wires both save-match persistence and final
     * ranking registration.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param hostNickname original host nickname associated with the match
     * @param savedMatchService persistence service selected for the current runtime mode
     * @param rankingService ranking service used to register completed matches; nullable when the
     *                       runtime does not expose ranking persistence
     */
    public GameOrchestrator(GameFactory gameFactory,
                            ArrayList<Player> players,
                            String hostNickname,
                            SavedMatchService savedMatchService,
                            RankingService rankingService) {
        Objects.requireNonNull(gameFactory, "Game factory cannot be null");
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        this.game = gameFactory.createGame(players);
        this.players = List.copyOf(players);
        this.hostNickname = requireHostNickname(hostNickname);
        this.rankingService = rankingService;
        this.persistenceCoordinator = new MatchPersistenceCoordinator(savedMatchService, this.hostNickname);
    }

    /**
     * Creates an orchestrator starting from an already restored game aggregate.
     *
     * @param restoredGame previously persisted game state reconstructed from storage
     */
    public GameOrchestrator(Game restoredGame) {
        this(
                restoredGame,
                resolveDefaultHostNickname(restoredGame.getPlayers()),
                NoOpSavedMatchService.INSTANCE
        );
    }

    /**
     * Creates an orchestrator starting from an already restored game aggregate and wires match persistence.
     *
     * @param restoredGame previously persisted game state reconstructed from storage
     * @param hostNickname original host nickname associated with the match
     * @param savedMatchService persistence service selected for the current runtime mode
     */
    public GameOrchestrator(Game restoredGame, String hostNickname, SavedMatchService savedMatchService) {
        this(restoredGame, hostNickname, savedMatchService, null);
    }

    /**
     * Creates an orchestrator starting from an already restored game aggregate and wires both
     * save-match persistence and final ranking registration.
     *
     * @param restoredGame previously persisted game state reconstructed from storage
     * @param hostNickname original host nickname associated with the match
     * @param savedMatchService persistence service selected for the current runtime mode
     * @param rankingService ranking service used to register completed matches; nullable when the
     *                       runtime does not expose ranking persistence
     */
    public GameOrchestrator(Game restoredGame,
                            String hostNickname,
                            SavedMatchService savedMatchService,
                            RankingService rankingService) {
        this.game = Objects.requireNonNull(restoredGame, "Restored game cannot be null");
        this.players = restoredGame.getPlayers();
        if (this.players.isEmpty()) {
            throw new IllegalArgumentException("Restored game must contain at least one player");
        }
        this.hostNickname = requireHostNickname(hostNickname);
        this.rankingService = rankingService;
        this.persistenceCoordinator = new MatchPersistenceCoordinator(savedMatchService, this.hostNickname);
    }

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
     * Returns whether this match is backed by real persistence.
     *
     * @return {@code true} when save-match persistence is active
     */
    public boolean isPersistenceEnabled() {
        return this.persistenceCoordinator.isPersistenceEnabled();
    }

    /**
     * Returns whether the match has been frozen after a persistence failure.
     *
     * @return {@code true} when gameplay commands are no longer accepted
     */
    public boolean isFrozen() {
        return this.frozen;
    }

    /**
     * Registers one observer for this active match.
     *
     * <p>Duplicate registrations are ignored so the same transport session is notified at most
     * once per successful mutation.
     *
     * @param observer observer interested in match updates
     */
    public synchronized void addObserver(MatchObserver observer) {
        this.observers.add(Objects.requireNonNull(observer, "observer cannot be null"));
    }

    /**
     * Removes one observer from this active match.
     *
     * <p>The operation is safe to call even when the observer is not currently registered.
     *
     * @param observer observer to remove
     */
    public synchronized void removeObserver(MatchObserver observer) {
        if (observer == null) {
            return;
        }
        this.observers.remove(observer);
    }

    /**
     * Performs match setup and persists the initial checkpoint when configured.
     */
    public void setupMatch() {
        this.ensureMatchIsWritable();
        this.game.setup();
        this.persistOrFreeze("GAME_INITIALIZED", () -> this.persistenceCoordinator.recordMatchInitialized(this.game));
        this.notifyObservers();
    }

    /**
     * Places a player's totem and persists the start-of-turn checkpoint when the draw phase starts.
     *
     * @param player acting player
     * @param tile target offer-space code
     */
    public void moveTotem(Player player, String tile) {
        this.ensureMatchIsWritable();
        RoundPhase previousRoundPhase = this.game.getTurnOrderTrack().getCurrentRoundPhase();
        this.game.moveTotem(player, tile);
        this.persistOrFreeze(
                "TURN_STARTED",
                () -> this.persistenceCoordinator.recordAfterMoveTotem(this.game, previousRoundPhase)
        );
        this.notifyObservers();
    }

    /**
     * Delegates one standard card draw and persists a turn-finished checkpoint when the draw turn
     * advances to another player or phase.
     *
     * @param player acting player
     * @param card chosen card
     */
    public void chooseCard(Player player, Card card) {
        this.ensureMatchIsWritable();
        Player previousPlayer = this.game.getTurnOrderTrack().getCurrentPlayer();
        RoundPhase previousRoundPhase = this.game.getTurnOrderTrack().getCurrentRoundPhase();
        this.game.chooseCard(player, card);

        if (this.didTurnAdvance(previousPlayer, previousRoundPhase)) {
            this.persistOrFreeze("TURN_FINISHED", () -> this.persistenceCoordinator.recordTurnFinished(this.game));
        }
        this.notifyObservers();
    }

    /**
     * Delegates one extra draw and persists a turn-finished checkpoint when the bonus-draw flow
     * advances to a new player or phase.
     *
     * @param player acting player
     * @param card chosen extra-draw card
     */
    public void extraDrawn(Player player, Card card) {
        this.ensureMatchIsWritable();
        Player previousPlayer = this.game.getTurnOrderTrack().getCurrentPlayer();
        RoundPhase previousRoundPhase = this.game.getTurnOrderTrack().getCurrentRoundPhase();
        this.game.extraDrawn(player, card);

        if (this.didTurnAdvance(previousPlayer, previousRoundPhase)) {
            this.persistOrFreeze("TURN_FINISHED", () -> this.persistenceCoordinator.recordTurnFinished(this.game));
        }
        this.notifyObservers();
    }

    /**
     * Resolves round effects and persists the checkpoint implied by the resulting phase.
     */
    public void resolveEffects() {
        this.ensureMatchIsWritable();
        this.game.resolveEffects();
        RoundPhase currentRoundPhase = this.game.getTurnOrderTrack().getCurrentRoundPhase();

        if (currentRoundPhase == RoundPhase.FINAL_SCORING) {
            this.persistOrFreeze("MATCH_COMPLETED", () -> this.persistenceCoordinator.recordMatchCompleted(this.game));
            this.notifyObservers();
            return;
        }

        if (currentRoundPhase == RoundPhase.RESETTING_BOARD) {
            this.persistOrFreeze("TURN_FINISHED", () -> this.persistenceCoordinator.recordTurnFinished(this.game));
        }
        this.notifyObservers();
    }

    /**
     * Resets the board for the next round and persists either an era-changed or turn-started
     * checkpoint depending on the resulting era.
     */
    public void resetBoard() {
        this.ensureMatchIsWritable();
        var previousEra = this.game.getCurrentEra();
        this.game.resetBoard();

        if (this.game.getCurrentEra() != previousEra) {
            this.persistOrFreeze("ERA_CHANGED", () -> this.persistenceCoordinator.recordEraChanged(this.game));
            this.notifyObservers();
            return;
        }

        this.persistOrFreeze("TURN_STARTED", () -> this.persistenceCoordinator.recordTurnStarted(this.game));
        this.notifyObservers();
    }

    /**
     * Applies final scoring and then executes the application-level completion flow.
     *
     * <p>The completed-match snapshot is persisted first so the finished match remains recoverable
     * even if a later ranking update fails. When a ranking service is configured, the method also
     * records the completed match and returns the resulting ranking payload.
     *
     * @return ranking payload when the runtime is configured to register completed matches;
     *         {@link Optional#empty()} otherwise
     */
    public Optional<RankingResult> calculateFinalScore() {
        this.ensureMatchIsWritable();
        this.game.calculateFinalScore();
        Optional<RankingResult> rankingResult = this.finalizeCompletedMatchOrFreeze();
        this.notifyObservers();
        return rankingResult;
    }

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

    private void ensureMatchIsWritable() {
        if (this.frozen) {
            throw new MatchFrozenException();
        }
    }

    private void persistOrFreeze(String checkpointName, Runnable saveAction) {
        try {
            saveAction.run();
        } catch (RuntimeException exception) {
            this.frozen = true;
            throw new MatchPersistenceFailureException(checkpointName, exception);
        }
    }

    private Optional<RankingResult> finalizeCompletedMatchOrFreeze() {
        try {
            this.persistenceCoordinator.recordMatchCompleted(this.game);
            if (this.rankingService == null) {
                return Optional.empty();
            }

            RankingResult rankingResult = this.rankingService.recordMatchAndGetRanking(
                    String.valueOf(this.game.getId()),
                    this.players.size(),
                    Instant.now(),
                    this.hostNickname,
                    this.buildFinalScores()
            );
            return Optional.of(rankingResult);
        } catch (RuntimeException exception) {
            this.frozen = true;
            throw new MatchFinalizationFailureException(exception);
        }
    }

    public synchronized GamePayload getCurrentPayload() {
        long currentVersion = this.game.getStateVersion();
        if (this.cachedPayload == null || this.cachedPayloadVersion != currentVersion) {
            this.cachedPayload = this.buildPayload();
            this.cachedPayloadVersion = currentVersion;
        }
        return this.cachedPayload;
    }

    public GamePayload buildPayload() {
        TurnOrderPayload turnOrder = buildTurnOrderPayload();
        TurnStatePayload turnState = new TurnStatePayload(
                this.game.getTurnState().getPhase().name()
        );
        List<PlayerPayload> players = this.game.getPlayers().stream()
                .map(this::toPlayerPayload)
                .toList();
        BoardPayload board = buildBoardPayload();

        return new GamePayload(
                this.game.getId(),
                this.game.getStatus().name(),
                this.game.getCurrentEra().ordinal(),
                turnOrder,
                turnState,
                players,
                board
        );
    }

    private TurnOrderPayload buildTurnOrderPayload() {
        List<Player> order;
        try {
            order = this.game.getTurnOrderTrack().getTurnOrder();
        } catch (IllegalStateException e) {
            order = List.of();
        }

        String currentPlayerNickname = order.isEmpty()
                ? null
                : this.game.getTurnOrderTrack().getCurrentPlayer().getNickname();

        return new TurnOrderPayload(
                this.game.getTurnOrderTrack().getRoundNumber(),
                this.game.getTurnOrderTrack().getCurrentRoundPhase().name(),
                currentPlayerNickname,
                order.stream().map(Player::getNickname).toList()
        );
    }

    private PlayerPayload toPlayerPayload(Player player) {
        List<CardPayload> characters = player.getTribe().getCharacters().stream()
                .map(this::toCardPayload)
                .toList();
        List<CardPayload> buildings = player.getTribe().getBuildings().stream()
                .map(this::toCardPayload)
                .toList();
        TribePayload tribe = new TribePayload(characters, buildings);

        return new PlayerPayload(
                player.getNickname(),
                player.getFood(),
                player.getPrestigePoints(),
                player.getTotem().name(),
                tribe
        );
    }

    private CardPayload toCardPayload(Card card) {
        return new CardPayload(
                card.getId(),
                card.getClass().getSimpleName(),
                card.getEra().ordinal()
        );
    }

    private BoardPayload buildBoardPayload() {
        List<CardPayload> upperRow = this.game.getBoard().getUpperRowCards().stream()
                .map(this::toCardPayload)
                .toList();
        List<CardPayload> lowerRow = this.game.getBoard().getLowerRowCards().stream()
                .map(this::toCardPayload)
                .toList();
        List<OfferSpacePayload> offerSpaces = this.game.getBoard().getOfferSpaces().stream()
                .map(this::toOfferSpacePayload)
                .toList();
        List<StartingBlockPayload> startingBlocks = this.game.getBoard().getStartingBlocks().stream()
                .map(this::toStartingBlockPayload)
                .toList();

        return new BoardPayload(upperRow, lowerRow, offerSpaces, startingBlocks);
    }

    private OfferSpacePayload toOfferSpacePayload(OfferSpace space) {
        String occupiedBy = space.getOccupiedBy() == null ? null : space.getOccupiedBy().getNickname();
        return new OfferSpacePayload(
                space.getCode(),
                space.getTopRowCardsToTake(),
                space.getBottomRowCardsToTake(),
                occupiedBy
        );
    }

    private StartingBlockPayload toStartingBlockPayload(StartingBlock block) {
        String occupiedBy = block.getOccupiedBy() == null ? null : block.getOccupiedBy().getNickname();
        return new StartingBlockPayload(
                block.getPosition(),
                block.getFoodModifier(),
                block.getPrestigeModifier(),
                occupiedBy
        );
    }

    private List<MatchScore> buildFinalScores() {
        return this.players.stream()
                .map(player -> new MatchScore(player.getNickname(), player.getPrestigePoints()))
                .toList();
    }

    private boolean didTurnAdvance(Player previousPlayer, RoundPhase previousRoundPhase) {
        return !this.game.getTurnOrderTrack().getCurrentPlayer().equals(previousPlayer)
                || previousRoundPhase != this.game.getTurnOrderTrack().getCurrentRoundPhase();
    }

    private void notifyObservers() {
        List<MatchObserver> observersSnapshot;
        List<MatchObserver> failedObservers = new ArrayList<>();
        synchronized (this) {
            observersSnapshot = new ArrayList<>(this.observers);
        }

        for (MatchObserver observer : observersSnapshot) {
            try {
                observer.onMatchUpdated();
            } catch (RuntimeException exception) {
                System.err.println("Failed to deliver match update to observer "
                        + observer.getClass().getSimpleName()
                        + ": "
                        + exception.getMessage());
                failedObservers.add(observer);
            }
        }

        if (!failedObservers.isEmpty()) {
            synchronized (this) {
                this.observers.removeAll(failedObservers);
            }
        }
    }
}
