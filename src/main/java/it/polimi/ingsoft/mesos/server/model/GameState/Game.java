package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;

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
    private final Board board;
    private final TurnOrderTrack turnOrderTrack;
    private final TurnState turnState;

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

    // Setters

    /**
     * Updates the current era tracked by the match.
     *
     * @param currentEra new era reached by the game
     */
    public void setCurrentEra(Era currentEra) {
        this.currentEra = currentEra;
    }

    /**
     * Updates the lifecycle status of the game.
     *
     * @param newstatus new status value for the match
     */
    public void setStatus(GameStatus newstatus){
        this.status = newstatus;
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
        if (this.status != GameStatus.SETUP) {
            throw new InvalidGameStatusException("setup", this.status);
        }
        this.board.buildOfferPath();

        this.board.setupInitialRows();

        this.turnOrderTrack.setNewOrder(players);
        this.turnOrderTrack.advanceRoundNumber();
        this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);

        this.turnState.resetForNewRound();

        this.status = GameStatus.IN_PROGRESS;
    }

    //After the setup and at the start of every round, following the order of the turnordertrack, users can ask
    //to place the totem.
    //When we reach the final user, we ask to the board the action order to insert in the turnorder track
    //If there are any, we resolve player that are on a tile that prescribres only food gaining and not cards to drawn
    public void moveTotem(Player player, String tile){
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

    }

    //Following the turn imposed the turnordertrack, and checking with the fsm and with the board we make an user
    //draw a card, after the player has finished to draw all his card, he come back to the initial tile,
    //and we reset the fsm for another player to draw
    public void chooseCard(Player player, Card card){
        this.validateChooseCardRequest(player);
        this.ensureDrawStateStarted(player);

        CardRow cardRow = this.validateAndRemoveChosenCard(player, card);
        this.applyChosenCardToPlayer(player, card);

        this.turnState.onCardChosen(player, cardRow);

        if (this.turnState.isTurnCompleted()) {
            this.completeDrawTurn(player);
        }

    }

    //After everyone has drawned and after everyone has come back to the starting position, if a player has a certain
    //building, he can drawn an extra card
    public void extraDrawn(Player player, Card card){
        this.validateExtraDrawRequest(player);

        CardRow cardRow = this.validateAndRemoveExtraDrawCard(player, card);
        this.applyChosenCardToPlayer(player, card);
        this.completeExtraDrawTurn();
    }

    //Adter the extradrawn we resolve the effect using the board
    //If wr are at round ten we go for calculatefinalscore
    public void resolveEffects(){
        this.validateResolveEffectsRequest();
        this.board.resolveLowerEvents();
        if (this.turnOrderTrack.getRoundNumber() == 10) {
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.FINAL_SCORING);
            return;
        }
        this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESETTING_BOARD);

    }

    //We reset the board and prepare the turnordertrack with the new order for the next totem position,
    //if we are
    public void resetBoard(){
        this.validateResetBoardRequest();

        boolean eraChanged = this.board.resetBoardAfterRound(this.currentEra);
        if (eraChanged) {
            this.currentEra = this.currentEra.next();
        }

        this.turnOrderTrack.advanceRoundNumber();
        this.turnState.resetForNewRound();
        this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);


    }

    public void calculateFinalScore(){
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
    }

    // Private methods

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

    private void ensureDrawStateStarted(Player player) {
        if (this.turnState.isTurnCompleted()) {
            this.turnState.startCardSelection(player);
        }

        if (!this.turnState.isWaitingForCardSelection()) {
            throw new InvalidTurnPhaseException("chooseCard", this.turnState.getPhase());
        }
    }

    private CardRow validateAndRemoveChosenCard(Player player, Card card) {
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
            return cardRow;
        }

        if (!this.board.getDraftableLowerCards(player).contains(card)) {
            throw new CardNotDrawableException(player, card);
        }
        if (!this.turnState.canChooseFromLowerRow(player)) {
            throw new CardRowSelectionNotAllowedException(cardRow);
        }
        this.board.removeLowerCard(card);
        return cardRow;
    }

    private void applyChosenCardToPlayer(Player player, Card card) {
        if (card instanceof BuildingCard buildingCard) {
            buildingCard.payEffectiveCost(player);
            player.getTribe().addBuilding(buildingCard);
        } else {
            player.getTribe().addCharacter((CharacterCard) card, player);
        }
    }

    private void completeDrawTurn(Player player) {
        this.board.returnPlayerToStartingTile(player);
        this.turnState.resetForNextPlayer(player);

        if (this.turnOrderTrack.isLastPlayer()) {
            this.transitionAfterPlayerDraws();
            return;
        }

        this.turnOrderTrack.advanceToNextPlayer();
    }

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

    private CardRow validateAndRemoveExtraDrawCard(Player player, Card card) {
        CardRow cardRow = this.board.getCardRow(card);
        if (cardRow == CardRow.NOT_PRESENT) {
            throw new CardNotPresentException(card);
        }

        if (cardRow == CardRow.UPPER) {
            if (!this.board.getDraftableUpperCards(player).contains(card)) {
                throw new CardNotDrawableException(player, card);
            }
            this.board.removeUpperCard(card);
            return cardRow;
        }

        if (!this.board.getDraftableLowerCards(player).contains(card)) {
            throw new CardNotDrawableException(player, card);
        }
        this.board.removeLowerCard(card);
        return cardRow;
    }

    private void completeExtraDrawTurn() {
        if (this.turnOrderTrack.isLastPlayer()) {
            this.turnOrderTrack.setNewOrder(this.board.getNextRoundTurnOrder());
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
            return;
        }

        this.turnOrderTrack.advanceToNextPlayer();
    }

    private void transitionAfterPlayerDraws() {
        List<Player> nextRoundOrder = this.board.getNextRoundTurnOrder();
        List<Player> extraDrawOrder = nextRoundOrder.stream()
                .filter(player -> player.getTribe().hasEndRoundExtraCardBuilding())
                .toList();

        if (extraDrawOrder.isEmpty()) {
            this.turnOrderTrack.setNewOrder(nextRoundOrder);
            this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
            return;
        }

        this.turnOrderTrack.setNewOrder(extraDrawOrder);
        this.turnOrderTrack.setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);
    }

    private void validateResolveEffectsRequest() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("resolveEffects", this.status);
        }

        if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.RESOLVING_EFFECTS) {
            throw new InvalidGamePhaseException("resolveEffects", this.turnOrderTrack.getCurrentRoundPhase());
        }
    }

    private void validateResetBoardRequest() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("resetBoard", this.status);
        }

        if (this.turnOrderTrack.getCurrentRoundPhase() != RoundPhase.RESETTING_BOARD) {
            throw new InvalidGamePhaseException("resetBoard", this.turnOrderTrack.getCurrentRoundPhase());
        }
    }
}
