package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

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
    private final ArrayList<Player> players;
    private Era currentEra; //Mutable
    private GameStatus status;  //Mutable
    private RoundPhase currentRoundPhase;
    private final Board board;
    private final TurnOrderTrack turnOrderTrack;
    private final TurnState turnState;

    /**
     * Creates a new game aggregate with all runtime collaborators already prepared by the factory.
     *
     * @param id unique game identifier
     * @param players players participating in the match
     * @param turnState finite-state machine controlling the current player's turn
     * @param turnOrderTrack tracker for the order of play inside each round
     * @param board board that exposes rows, offer path, and available spaces
     * @param tribeDeck tribe deck used by the board and by later game phases
     * @param buildingDeck building deck used by the board and by later game phases
     */
    public Game(int id, ArrayList<Player> players, TurnState turnState, TurnOrderTrack turnOrderTrack, Board board) {
        this.id = id;
        this.players = players;
        this.turnState = turnState;
        this.turnOrderTrack = turnOrderTrack;
        this.board = board;
        this.currentEra = Era.first;
        this.status = GameStatus.SETUP;
    }

    //Setter

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

    //Getters

    /**
     * Returns the players that belong to this game.
     *
     * @return mutable list of players tracked by the game
     */
    public ArrayList<Player> getPlayers(){
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
     * Performs the initial game setup.
     *
     * <p>The setup builds the offer path on the board, fills the visible card rows, initializes the
     * round order from the player list, and transitions the game from {@code SETUP} to
     * {@code IN_PROGRESS}.
     */
    public void setup(){
        if (this.status != GameStatus.SETUP) {
            throw new InvalidGameStatusException("setup", this.status);
        }
        this.board.buildOfferPath();

        this.board.setupInitialRows();

        this.turnOrderTrack.setNewOrder(players);

        this.turnState.resetForNewRound();

        this.status = GameStatus.IN_PROGRESS;
        this.currentRoundPhase = RoundPhase.PLACING_TOTEMS;
    }

    /**
     * Applies the current player's totem move to the requested offer-space tile.
     *
     * <p>The method validates turn ownership, checks that the turn phase allows moving the totem,
     * verifies that the destination space is available, places the player on the board, and updates
     * the turn-state machine with the number of cards the player is allowed to draw from each row.
     *
     * @param player player attempting the move
     * @param tilecode code of the destination tile on the offer path
     */
    public void moveTotem(Player player, String tilecode){

        if(this.status !=  GameStatus.IN_PROGRESS) {throw new InvalidGameStatusException("moveTotem", this.status);}

        if(this.currentRoundPhase !=  RoundPhase.PLACING_TOTEMS) {
            throw  new InvalidGamePhaseException("moveTotem", this.currentRoundPhase);
        }

        if(!turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
        }
        if(!this.turnState.canMoveTotem()){
            throw new InvalidTurnPhaseException("moveTotem", this.turnState.getPhase());
        }

        if(tilecode == null || !board.isSpaceAvailable(tilecode)){throw new TileNotAvailableException(tilecode);}

        board.placePlayerOnOfferSpace(player, tilecode);

        if(turnOrderTrack.isLastPlayer()){
            this.currentRoundPhase = RoundPhase.PLAYER_TURNS;
            List<Player> resolutionOrder = this.board.getResolutionOrder();
            this.turnOrderTrack.setNewOrder(resolutionOrder);
            boolean skipFirstPlayer = this.board.resolveEventA();
            for (Player resolvedPlayer : resolutionOrder) {
                int[] drawableCards = this.board.returnNumberDrawableCard(resolvedPlayer);
                this.turnState.onTotemMoved(resolvedPlayer, drawableCards[0], drawableCards[1]);
            }
            if (skipFirstPlayer && !this.turnOrderTrack.isLastPlayer()) {
                this.turnOrderTrack.advanceToNextPlayer();
            }
            this.turnState.startCardSelection(this.turnOrderTrack.getCurrentPlayer());
        }else{
            this.turnOrderTrack.advanceToNextPlayer();
        }
    }



    /**
     * Resolves a card draft performed by the current player.
     *
     * <p>The method enforces game/round/turn preconditions, verifies that the selected card is
     * present on the board and currently drawable by the player, removes it from the proper row,
     * and applies card effects to the player's tribe (including building cost payment when
     * required). It then updates the turn finite-state machine and, when the turn ends, advances
     * the turn order or transitions the round to {@link RoundPhase#RESOLVING_EVENTS}.
     *
     * @param player player selecting the card
     * @param card card chosen from the board
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code PLAYER_TURNS}
     * @throws NotPlayersTurnException if {@code player} is not the active player
     * @throws InvalidTurnPhaseException if the turn state is not waiting for a card selection
     * @throws CardNotPresentException if the selected card is not currently on the board
     * @throws CardNotDrawableException if the selected card cannot be drafted by {@code player}
     * @throws CardRowSelectionNotAllowedException if the selected row is not currently selectable
     */
    public void chooseCard(Player player, Card card){

        if(this.status !=  GameStatus.IN_PROGRESS) {throw new InvalidGameStatusException("chooseCard", this.status);}

        if(this.currentRoundPhase !=  RoundPhase.PLAYER_TURNS) {throw  new InvalidGamePhaseException("chooseCard",this.currentRoundPhase);}

        if(!turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
        }

        if (!turnState.isWaitingForCardSelection()) {
            throw new InvalidTurnPhaseException("chooseCard", turnState.getPhase());
        }

        CardRow cardRow = board.getCardRow(card);
        if(cardRow == CardRow.NOT_PRESENT){
            throw  new CardNotPresentException(card);
        }
        if (cardRow == CardRow.UPPER) {
            if (!board.getDraftableUpperCards(player).contains(card)) {
                throw new CardNotDrawableException(player, card);
            }
            if(!turnState.canChooseFromUpperRow()){
                throw new CardRowSelectionNotAllowedException(cardRow);
            }
            board.removeUpperCard(card);
            if (card instanceof BuildingCard){
                ((BuildingCard) card).payEffectiveCost(player);
                player.getTribe().addBuilding((BuildingCard) card);
            }else{
                player.getTribe().addCharacter((CharacterCard) card, player);
            }
            turnState.onUpperCardChosen();
        }else{
            if (!board.getDraftableLowerCards(player).contains(card)) {
                throw new CardNotDrawableException(player, card);
            }
            if(!turnState.canChooseFromLowerRow()){
                throw new CardRowSelectionNotAllowedException(cardRow);
            }
            board.removeLowerCard(card);
            if (card instanceof BuildingCard){
                ((BuildingCard) card).payEffectiveCost(player);
                player.getTribe().addBuilding((BuildingCard) card);
            }else{
                player.getTribe().addCharacter((CharacterCard) card, player);
            }
            turnState.onLowerCardChosen();

        }
        if (turnState.isTurnCompleted()) {
            turnState.resetForNextPlayer();
            if(turnOrderTrack.isLastPlayer()){
                this.currentRoundPhase = RoundPhase.RESOLVING_EVENTS;
                this.turnState.resetForNewRound();
            }else {
                turnOrderTrack.advanceToNextPlayer();
                turnState.startCardSelection(this.turnOrderTrack.getCurrentPlayer());
            }

        }

    }
    /**
     * Resolves end-of-round events and advances the round flow.
     *
     * <p>This action is available only after all players have completed their turn choices. It
     * delegates event resolution to the board and then transitions the round either to
     * {@link RoundPhase#FINAL_SCORING} (after round 10) or to
     * {@link RoundPhase#RETURNING_TO_START}.
     *
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code RESOLVING_EVENTS}
     */
    public void resolveEvents(){
        if(this.status !=  GameStatus.IN_PROGRESS) {throw new InvalidGameStatusException("ResolveEvents", this.status);}
        if(this.currentRoundPhase !=  RoundPhase.RESOLVING_EVENTS ){throw  new InvalidGamePhaseException("resolveEvents", this.currentRoundPhase);}

        board.resolveLowerEvents();

        if (this.turnOrderTrack.getRoundNumber() == 10) {
            this.currentRoundPhase = RoundPhase.FINAL_SCORING;
        } else {
            this.currentRoundPhase = RoundPhase.RETURNING_TO_START;
        }
    }

    /**
     * Rebuilds player order from totem positions and prepares board reset.
     *
     * <p>The board computes the new order from the offer path, and the turn-order tracker is
     * updated accordingly. The round then advances to {@link RoundPhase#RESETTING_BOARD}.
     *
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code RETURNING_TO_START}
     */
    public void returnToStart() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("returnToStart", this.status);
        }

        if (this.currentRoundPhase != RoundPhase.RETURNING_TO_START) {
            throw new InvalidGamePhaseException("returnToStart", this.currentRoundPhase);
        }

        List<Player> newOrder = this.board.getResolutionOrder();
        for (Player player : newOrder) {
            this.board.returnPlayerToStartingTile(player);
        }
        this.turnOrderTrack.setNewOrder(newOrder);
        this.turnState.resetForNewRound();
        this.currentRoundPhase = RoundPhase.RESETTING_BOARD;
    }

    /**
     * Resets board rows at the end of a round and handles era progression.
     *
     * <p>The board performs the round reset (card shifts/refills). If the reset reports an era
     * transition, the current era is advanced. The round phase is then moved to
     * {@link RoundPhase#POST_REFILL_DRAW}.
     *
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code RESETTING_BOARD}
     */
    public void resetBoard() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("resetBoard", this.status);
        }

        if (this.currentRoundPhase != RoundPhase.RESETTING_BOARD) {
            throw new InvalidGamePhaseException("resetBoard", this.currentRoundPhase);
        }

        boolean eraChanged = board.resetBoardAfterRound(this.currentEra);
        if (eraChanged) {
            this.currentEra = this.currentEra.next();
        }

        this.currentRoundPhase = RoundPhase.POST_REFILL_DRAW;
    }
    /**
     * Completes the post-refill draw window and reopens normal player turns.
     *
     * <p>The current implementation only validates state/phase and advances the round phase to
     * {@link RoundPhase#PLAYER_TURNS}. Additional post-refill draw logic can be introduced here
     * later without changing the external transition contract.
     *
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code POST_REFILL_DRAW}
     */
    public void postRefillDrawn(){
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("postRefillDrawn", this.status);
        }

        if (this.currentRoundPhase != RoundPhase.POST_REFILL_DRAW) {
            throw new InvalidGamePhaseException("postRefillDrawn", this.currentRoundPhase);
        }

        this.currentRoundPhase = RoundPhase.PLAYER_TURNS;

    }


    /**
     * Entry point for final scoring after the last round.
     *
     * <p>The method currently validates that the game is in progress and that the round phase is
     * {@link RoundPhase#FINAL_SCORING}. Scoring side effects are intentionally not implemented
     * yet.
     *
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code FINAL_SCORING}
     */
    public void calculateFinalScore(){
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("calculateFinalScore", this.status);
        }

        if (this.currentRoundPhase != RoundPhase.FINAL_SCORING) {
            throw new InvalidGamePhaseException("calculateFinalScore", this.currentRoundPhase);
        }

        for(Player p: this.players){
            for (BuildingCard b: p.getTribe().getBuildings()){
                b.increaseFinalPrestigePoints(p);   // All pp of the building
                b.applyEffect(p);                   // All the final effect of the building
                p.gainPrestigePoints(p.getTribe().getPrestigePointsForBuilderInFinalPhase());  // Bonus for builder cards
            }
        }

    }

}
