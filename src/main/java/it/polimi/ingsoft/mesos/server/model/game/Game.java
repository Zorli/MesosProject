package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnStatePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//References : docs/design-doc-game-state.md

/**
 * Aggregate root that stores the full mutable state of one Mesos match.
 *
 * <p>The class owns the players, decks, board, current era, turn-state machine, and round-order
 * tracker. It exposes the high-level actions invoked by the orchestrator and applies the
 * corresponding state transitions to the match.
 */
public class Game {
    private final int id;   //Immutable, provided by the game orchestrator
    private final List<Player> players;
    private Era currentEra; //Mutable
    private GameStatus status;  //Mutable
    private long stateVersion;
    private final Board board;
    private final TurnOrderTrack turnOrderTrack;
    private final TurnState turnState;
    private final List<Listener> listeners;

    // Constructors

    /**
     * Creates a new game aggregate with all runtime collaborators already prepared by the factory.
     *
     * @param id unique game identifier
     * @param players players participating in the match
     * @param turnState finite-state machine controlling the current player's turn
     * @param turnOrderTrack tracker for the order of play inside each round
     * @param board board that exposes rows, offer path, and available spaces
     */
    public Game(int id, List<Player> players, TurnState turnState, TurnOrderTrack turnOrderTrack, Board board) {
        this.id = id;
        this.players = List.copyOf(Objects.requireNonNull(players, "Players cannot be null"));
        this.turnState = turnState;
        this.turnOrderTrack = turnOrderTrack;
        this.board = board;
        this.currentEra = Era.first;
        this.status = GameStatus.SETUP;
        this.stateVersion = 0L;
        this.listeners = new ArrayList<>();
    }

    /**
     * Creates a game aggregate from an already restored runtime snapshot.
     *
     * @param id unique game identifier
     * @param players players participating in the match
     * @param turnState restored turn-state machine
     * @param turnOrderTrack restored round-order tracker
     * @param board restored board and decks
     * @param currentEra restored current era
     * @param status restored lifecycle status
     */
    public Game(int id,
                List<Player> players,
                TurnState turnState,
                TurnOrderTrack turnOrderTrack,
                Board board,
                Era currentEra,
                GameStatus status) {
        this.id = id;
        this.players = List.copyOf(Objects.requireNonNull(players, "Players cannot be null"));
        this.turnState = turnState;
        this.turnOrderTrack = turnOrderTrack;
        this.board = board;
        this.currentEra = currentEra;
        this.status = status;
        this.stateVersion = 0L;
        this.listeners = new ArrayList<>();
    }

    // Getters

    /**
     * Returns the players that belong to this game.
     *
     * @return read-only list of players tracked by the game
     */
    public List<Player> getPlayers(){
        return this.players;
    }

    /**
     * Returns the unique identifier of this match.
     *
     * @return game identifier
     */
    public int  getId(){
        return this.id;
    }

    /**
     * Returns the era currently being played.
     *
     * @return current game era
     */
    public Era getCurrentEra(){
        return this.currentEra;
    }

    /**
     * Returns the current lifecycle status of the match.
     *
     * @return game status
     */
    public GameStatus getStatus(){
        return this.status;
    }

    /**
     * Returns the monotonically increasing version of the mutable game state.
     *
     * @return current state version
     */
    public long getStateVersion() {
        return this.stateVersion;
    }

    /**
     * Returns the board currently owned by the game.
     *
     * @return current board
     */
    public Board getBoard() {
        return this.board;
    }

    /**
     * Returns the round-order tracker currently owned by the game.
     *
     * @return turn-order tracker
     */
    public TurnOrderTrack getTurnOrderTrack() {
        return this.turnOrderTrack;
    }

    /**
     * Returns the turn-state machine currently owned by the game.
     *
     * @return turn state
     */
    public TurnState getTurnState() {
        return this.turnState;
    }

    public void addListener(Listener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    // Setters

    /**
     * Updates the current era tracked by the match.
     *
     * @param currentEra new era reached by the game
     */
    public void setCurrentEra(Era currentEra) {
        this.currentEra = currentEra;
        this.markStateChanged();
    }

    /**
     * Updates the lifecycle status of the game.
     *
     * @param newstatus new status value for the match
     */
    public void setStatus(GameStatus newstatus){
        this.status = newstatus;
        this.markStateChanged();
    }

    // Public methods
    
    /**
     * Performs the initial game setup.
     *
     * <p>The setup builds the offer path on the board, fills the visible card rows, initializes the
     * first round order from the lobby join order stored in {@code players}, and transitions the
     * game from {@code SETUP} to {@code IN_PROGRESS}.
     */
    public void setup(){
        this.executeGameAction(() -> {
            if (this.status != GameStatus.SETUP) {
                throw new InvalidGameStatusException("setup", this.status);
            }
            this.board.buildOfferPath();

            this.board.setupInitialRows();
            applyInitialBenefit();

            this.turnOrderTrack.setNewOrder(players);
            this.turnOrderTrack.advanceRoundNumber();
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);

            this.turnState.resetForNewRound();
            this.status = GameStatus.IN_PROGRESS;
            this.markStateChanged();
        });
        GamePayload update = this.buildPayload();
        notifyListeners(update);
    }

    //TODO : Add method for choosing the starting tile, as now they are placed as subscribed in lobby
    //From rules sheet:
//    Piazzate casualmente i Totem negli spazi della tessera Ordine
//    di Turno. Questo definirà l’ordine di turno del primo round.

    /**
     * Places the current player's totem on the requested offer-path space.
     *
     * <p>The method validates match status, phase, turn ownership, and tile availability. After
     * placement it computes how many cards the player may later draw. Once the last player has
     * placed a totem, the offer path resolves the new draw order and the game transitions to the
     * player-draws phase.
     *
     * @param player player placing the totem
     * @param tile board-space identifier selected by the player
     */
    public void moveTotem(Player player, String tile){
        this.executeGameAction(() -> {
            if (this.status != GameStatus.IN_PROGRESS) {
                throw new InvalidGameStatusException("moveTotem", this.status);
            }

            if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.PLACING_TOTEMS) {
                throw new InvalidGamePhaseException("moveTotem", this.turnOrderTrack.getCurrentRoundPhase());
            }

            if (!this.turnOrderTrack.getCurrentPlayer().equals(player)) {
                throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
            }

            if (tile == null || !this.board.isSpaceAvailable(tile)) {
                throw new TileNotAvailableException(tile);
            }

            this.board.placePlayerOnOfferSpace(player, tile);

            int[] drawableCards = this.board.returnNumberDrawableCard(player);
            this.turnState.registerDrawableCards(player, drawableCards[0], drawableCards[1]);

            if (this.turnOrderTrack.isLastPlayer()) {
                this.board.resolveEventA();
                this.turnOrderTrack.setNewOrder(this.board.getResolutionOrder());
                while (!this.turnState.hasDrawableCards(this.turnOrderTrack.getCurrentPlayer())
                        && !this.turnOrderTrack.isLastPlayer()) {
                    this.turnOrderTrack.advanceToNextPlayer();
                }
                this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
            } else {
                this.turnOrderTrack.advanceToNextPlayer();
            }
            this.markStateChanged();
        });
        GamePayload update = this.buildPayload();
        notifyListeners(update);
    }
    /**
     * Makes the active player draw one card from the board.
     *
     * <p>The method validates the request against the current round phase, turn owner, state
     * machine, and board rows. The chosen card is removed from the board, applied to the player's
     * tribe, and the turn is advanced when no more choices remain.
     *
     * @param player player selecting the card
     * @param cardId cardId of the card to draw from the board
     */
    public void chooseCard(Player player, int cardId){
        this.executeGameAction(() -> {
            this.validateChooseCardRequest(player);
            this.ensureDrawStateStarted(player);

            ChosenCardResult result = this.validateAndRemoveChosenCard(player, cardId);
            this.applyChosenCardToPlayer(player, result.card);

            this.turnState.onCardChosen(player, result.row);

            if (this.turnState.isTurnCompleted() || !this.hasAnyAvailableDraw(player)) {
                this.completeDrawTurn(player);
            }
            this.markStateChanged();
        });
        GamePayload update = this.buildPayload();
        this.notifyListeners(update);
    }
    /**
     * Resolves an extra card draw granted by a previously acquired building effect.
     *
     * @param player player receiving the extra draw
     * @param cardId extra card chosen from the board
     */
    public void extraDrawn(Player player, int cardId){
        this.executeGameAction(() -> {
            this.validateExtraDrawRequest(player);
            //extraDrawn e chooseCard devono prendere in ingresso solo il cardId, questa struttura permette di ottenere
            //tutto ciò che serve dalla board con una chiamata
            ChosenCardResult result = this.validateAndRemoveExtraDrawCard(player, cardId);
            this.applyChosenCardToPlayer(player, result.card);
            this.completeExtraDrawTurn();
            this.markStateChanged();
        });
        GamePayload update = this.buildPayload();
        this.notifyListeners(update);
    }
    /**
     * Resolves lower-row events after all standard and bonus draws are completed.
     *
     * <p>If the match has reached round ten the method transitions directly to final scoring;
     * otherwise it advances the round flow to board reset.
     */
    public void resolveEffects(){
        this.executeGameAction(() -> {
            this.validateResolveEffectsRequest();

            if (this.turnOrderTrack.getRoundNumber() == 10) {
                this.board.resolveAllEvents();
                this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.FINAL_SCORING);
                this.markStateChanged();
                return;
            }
            this.board.resolveLowerEvents();
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESETTING_BOARD);
            this.markStateChanged();
        });
        GamePayload update = this.buildPayload();
        this.notifyListeners(update);
    }
    /**
     * Resets the board after event resolution and prepares the next round.
     *
     * <p>The reset may also advance the era when the board reports an era transition. At the end
     * of the method the round counter, turn state, and phase tracker are ready for the next totem
     * placement sequence.
     */
    public void resetBoard(){
        this.executeGameAction(() -> {
            this.validateResetBoardRequest();

            boolean eraChanged = this.board.resetBoardAfterRound(this.currentEra);
            if (eraChanged) {
                this.currentEra = this.currentEra.next();
            }

            this.turnOrderTrack.advanceRoundNumber();
            this.turnState.resetForNewRound();
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);
            this.markStateChanged();
        });
        GamePayload update = this.buildPayload();
        this.notifyListeners(update);
    }
    /**
     * Applies final-scoring rules to every player and closes the match.
     *
     * <p>The method evaluates final building effects, awards final prestige points, and transitions
     * the match status to {@code FINISHED}.
     */
    public void calculateFinalScore(){
        this.executeGameAction(() -> {
            if (this.status != GameStatus.IN_PROGRESS) {
                throw new InvalidGameStatusException("calculateFinalScore", this.status);
            }

            if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.FINAL_SCORING) {
                throw new InvalidGamePhaseException("calculateFinalScore", this.turnOrderTrack.getCurrentRoundPhase());
            }

            for (Player player : this.players) {
                for (BuildingCard building : player.getTribe().getBuildings()) {
                    building.increaseFinalPrestigePoints(player);
                    building.applyEffect(player);
                }
                player.gainPrestigePoints(player.getTribe().getPrestigePointsForBuilderInFinalPhase());
            }

            this.status = GameStatus.FINISHED;
            this.markStateChanged();
        });
    }

    // Private methods

    /**
     * Validates lifecycle status, round phase, and turn ownership for a standard card draw.
     *
     * @param player player attempting to draw a card
     */
    private void validateChooseCardRequest(Player player) {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("chooseCard", this.status);
        }

        if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.PLAYER_DRAWS) {
            throw new InvalidGamePhaseException("chooseCard", this.turnOrderTrack.getCurrentRoundPhase());
        }

        if (!this.turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
        }
    }

    /**
     * Ensures the turn state is ready to accept a card selection for the active player.
     *
     * @param player active player whose card selection is starting
     */
    private void ensureDrawStateStarted(Player player) {
        if (this.turnState.isTurnCompleted()) {
            this.turnState.startCardSelection(player);
        }

        if (!this.turnState.isWaitingForCardSelection()) {
            throw new InvalidTurnPhaseException("chooseCard", this.turnState.getPhase());
        }
    }

    /**
     * Verifies that the chosen card is currently drawable and removes it from the correct board row.
     *
     * @param player player attempting the draw
     * @param cardId chosen board card
     * @return board row the card was removed from
     */
    private ChosenCardResult validateAndRemoveChosenCard(Player player, int cardId) {
        Card card = this.board.findCardById(cardId);
        CardRow cardRow = this.board.getCardRow(card);
        if (cardRow == CardRow.NOT_PRESENT) {
            throw new CardNotPresentException(card);
        }

        if (cardRow == CardRow.UPPER) {
            if (!this.board.getDraftableUpperCards(player).contains(card)) {
                throw new CardNotDrawableException(player, card);
            }
            if (!this.turnState.canChooseFromUpperRow(player)) {
                throw new CardRowSelectionNotAllowedException(cardRow);
            }
            this.board.removeUpperCard(card);
            return new ChosenCardResult(card, cardRow);
        }

        if (!this.board.getDraftableLowerCards(player).contains(card)) {
            throw new CardNotDrawableException(player, card);
        }
        if (!this.turnState.canChooseFromLowerRow(player)) {
            throw new CardRowSelectionNotAllowedException(cardRow);
        }
        this.board.removeLowerCard(card);
        return new ChosenCardResult(card, cardRow);
    }

    /**
     * Applies the chosen card to the player's tribe, paying costs when the card is a building.
     *
     * @param player player receiving the card
     * @param card chosen card to apply
     */
    private void applyChosenCardToPlayer(Player player, Card card) {
        if (card instanceof BuildingCard buildingCard) {
            buildingCard.payEffectiveCost(player);
            player.getTribe().addBuilding(buildingCard);
        } else {
            player.getTribe().addCharacter((CharacterCard) card, player);
        }
    }

    /**
     * Finalizes a completed standard draw turn and advances the round flow.
     *
     * @param player player whose draw turn has just ended
     */
    private void completeDrawTurn(Player player) {
        this.board.returnPlayerToStartingTile(player);
        this.turnState.resetForNextPlayer(player);

        this.advanceToNextDrawablePlayerOrResolveRound();
    }

    /**
     * Validates lifecycle status, round phase, turn ownership, and player eligibility for an extra draw.
     *
     * @param player player attempting the bonus draw
     */
    private void validateExtraDrawRequest(Player player) {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("extraDrawn", this.status);
        }

        if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.EXTRA_DRAWS) {
            throw new InvalidGamePhaseException("extraDrawn", this.turnOrderTrack.getCurrentRoundPhase());
        }

        if (!this.turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
        }

        if (!player.getTribe().hasEndRoundExtraCardBuilding()) {
            throw new IllegalStateException("Player " + player.getNickname() + " cannot perform an extra draw");
        }
    }

    /**
     * Verifies that the requested extra-draw card is present and drawable, then removes it.
     *
     * @param player player performing the extra draw
     * @param cardId chosen extra-draw card
     * @return board row the card was removed from
     */
    private ChosenCardResult validateAndRemoveExtraDrawCard(Player player, int cardId) {
        Card card = this.board.findCardById(cardId);
        CardRow cardRow = this.board.getCardRow(card);
        if (cardRow == CardRow.NOT_PRESENT) {
            throw new CardNotPresentException(card);
        }

        if (cardRow == CardRow.UPPER) {
            if (!this.board.getDraftableUpperCards(player).contains(card)) {
                throw new CardNotDrawableException(player, card);
            }
            this.board.removeUpperCard(card);
            return new ChosenCardResult(card, cardRow);
        }

        if (!this.board.getDraftableLowerCards(player).contains(card)) {
            throw new CardNotDrawableException(player, card);
        }
        this.board.removeLowerCard(card);
        return new ChosenCardResult(card, cardRow);
    }

    /**
     * Advances the game after one bonus draw, either to the next eligible player or to effect resolution.
     */
    private void completeExtraDrawTurn() {
        if (this.turnOrderTrack.isLastPlayer()) {
            this.turnOrderTrack.setNewOrder(this.board.getNextRoundTurnOrder());
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
            return;
        }

        this.turnOrderTrack.advanceToNextPlayer();
    }

    /**
     * Chooses the next phase after all standard draws, filtering players eligible for extra draws.
     */
    private void transitionAfterPlayerDraws() {
        List<Player> nextRoundOrder = this.board.getNextRoundTurnOrder();
        List<Player> extraDrawOrder = nextRoundOrder.stream()
                .filter(player -> player.getTribe().hasEndRoundExtraCardBuilding())
                .filter(this::hasAnyDraftableCardOnBoard)
                .toList();

        if (extraDrawOrder.isEmpty()) {
            this.turnOrderTrack.setNewOrder(nextRoundOrder);
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
            return;
        }

        this.turnOrderTrack.setNewOrder(extraDrawOrder);
        this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);
    }

    /**
     * Advances to the next player who can still complete a real draw, or resolves the round when
     * no further standard draws are possible.
     */
    private void advanceToNextDrawablePlayerOrResolveRound() {
        while (true) {
            if (this.turnOrderTrack.isLastPlayer()) {
                this.transitionAfterPlayerDraws();
                return;
            }

            this.turnOrderTrack.advanceToNextPlayer();
            Player nextPlayer = this.turnOrderTrack.getCurrentPlayer();
            if (this.hasAnyAvailableDraw(nextPlayer)) {
                return;
            }

            this.board.returnPlayerToStartingTile(nextPlayer);
            this.turnState.resetForNextPlayer(nextPlayer);
        }
    }

    private boolean hasAnyAvailableDraw(Player player) {
        return this.hasAvailableUpperDraw(player) || this.hasAvailableLowerDraw(player);
    }

    /**
     * Returns whether the player can draft at least one board card, independently from turn-state
     * quotas. This is used for end-of-round extra draws, which do not consume tracked row choices.
     */
    //This method is used only for the extra drawn phase
    //in which we do not have to check for left card to drawn with turn state fsm
    private boolean hasAnyDraftableCardOnBoard(Player player) {
        return !this.board.getDraftableUpperCards(player).isEmpty()
                || !this.board.getDraftableLowerCards(player).isEmpty();
    }

    private boolean hasAvailableUpperDraw(Player player) {
        return this.turnState.getRemainingUpperChoices(player) > 0
                && !this.board.getDraftableUpperCards(player).isEmpty();
    }

    private boolean hasAvailableLowerDraw(Player player) {
        return this.turnState.getRemainingLowerChoices(player) > 0
                && !this.board.getDraftableLowerCards(player).isEmpty();
    }

    /**
     * Validates that lower-event resolution is being requested in a valid game state.
     */
    private void validateResolveEffectsRequest() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("resolveEffects", this.status);
        }

        if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.RESOLVING_EFFECTS) {
            throw new InvalidGamePhaseException("resolveEffects", this.turnOrderTrack.getCurrentRoundPhase());
        }
    }

    /**
     * Validates that board reset is being requested in a valid game state.
     */
    private void validateResetBoardRequest() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("resetBoard", this.status);
        }

        if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.RESETTING_BOARD) {
            throw new InvalidGamePhaseException("resetBoard", this.turnOrderTrack.getCurrentRoundPhase());
        }
    }


//    Infine, indipendentemente dal numero di giocatori,
//    il 1° giocatore riceve 2 Cibo, il 2° e il 3° giocatore ricevono
//     3 Cibo, mentre il 4° e il 5° giocatore ricevono 4 Cibo.
    private void applyInitialBenefit(){
        if (this.status != GameStatus.SETUP) {
            throw new InvalidGameStatusException("setup", this.status);
        }
        for (int i = 0; i < players.size(); i++) {
            Player currentPlayer = players.get(i);
            int initialFood;
            if (i == 0) {
                initialFood = 2;
            } else if (i == 1 || i == 2) {
                initialFood = 3;
            } else {
                initialFood = 4;
            }
            currentPlayer.gainFood(initialFood);

        }
    }

    private void markStateChanged() {
        this.stateVersion++;
    }

    public GamePayload buildPayload() {
        TurnOrderPayload turnOrder = this.buildTurnOrderPayload();
        TurnStatePayload turnState = new TurnStatePayload(this.turnState.getPhase().name());
        List<PlayerPayload> players = this.players.stream()
                .map(this::toPlayerPayload)
                .toList();
        BoardPayload board = this.buildBoardPayload();

        return new GamePayload(
                this.id,
                this.status.name(),
                this.currentEra.ordinal(),
                turnOrder,
                turnState,
                players,
                board
        );
    }

    //Praticamente le azioni diventanto dei runnable , delle lambda che runniamo qua dentro
    //Cosi unifichiamo dove e come vengono gestiti gli update in happy path
    //sia broadcast di errori
    private void executeGameAction(Runnable action) {
        long previousVersion = this.stateVersion;
        try {
            action.run();
            if (this.stateVersion != previousVersion) {
                this.notifyListeners(this.buildPayload());
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            this.notifyListeners(new ErrorPayload(exception.getClass().getSimpleName(), exception.getMessage()));
        }
    }

    private void notifyListeners(Object payload) {
        List<Listener> currentListeners = List.copyOf(this.listeners);
        for (Listener listener : currentListeners) {
            listener.notifyUpdate(payload);
        }
    }

    private TurnOrderPayload buildTurnOrderPayload() {
        List<Player> order;
        try {
            order = this.turnOrderTrack.getTurnOrder();
        } catch (IllegalStateException e) {
            order = List.of();
        }

        String currentPlayerNickname = order.isEmpty()
                ? null
                : this.turnOrderTrack.getCurrentPlayer().getNickname();

        return new TurnOrderPayload(
                this.turnOrderTrack.getRoundNumber(),
                this.turnOrderTrack.getCurrentRoundPhase().name(),
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
        List<CardPayload> upperRow = this.board.getUpperRowCards().stream()
                .map(this::toCardPayload)
                .toList();
        List<CardPayload> lowerRow = this.board.getLowerRowCards().stream()
                .map(this::toCardPayload)
                .toList();
        List<OfferSpacePayload> offerSpaces = this.board.getOfferSpaces().stream()
                .map(this::toOfferSpacePayload)
                .toList();
        List<StartingBlockPayload> startingBlocks = this.board.getStartingBlocks().stream()
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
    //tupla record per avere sia la row che la card in restituzione
    private record ChosenCardResult(Card card, CardRow row) {}
}
