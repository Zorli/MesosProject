package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;

import java.util.ArrayList;
import java.util.List;

//References : docs/design-doc-game-state.md

/**
 * Aggregate root that stores the full mutable state of one Mesos match.
 *
 * <p>The class owns the players, decks, board, current era, turn-state machine, and round-order
 * tracker. It exposes the high-level actions invoked by the orchestrator and translates them into
 * board updates plus immutable {@link ActionResult} snapshots that describe the new public state
 * of the match.
 */
public class Game {
    private final int id;   //Immutable, provided by the game orchestrator
    private final ArrayList<Player> players;
    private Era currentEra; //Mutable
    private GameStatus status;  //Mutable
    private RoundPhase currentRoundPhase;
    private Card currentCard;
    private TribeDeck tribeDeck; //Should I keep the reference to the deck directly, or I only need the board?
    private BuildingDeck buildingDeck;
    private Board board;
    private TurnOrderTrack turnOrderTrack;
    private TurnState turnState;
    //Need only the id and the number of players, the era it's clearly the first one

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
    public Game(int id, ArrayList<Player> players, TurnState turnState, TurnOrderTrack turnOrderTrack, Board board,
                TribeDeck  tribeDeck, BuildingDeck buildingDeck) {
        this.id = id;
        this.players = players;
        this.turnState = turnState;
        this.turnOrderTrack = turnOrderTrack;
        this.board = board;
        this.tribeDeck = tribeDeck;
        this.buildingDeck = buildingDeck;
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

    //I need to expose all the methods that a client could ask for advancing in the game
    //After every method call I should return an object state to inform the game orchestrator about the current
    //game state

    /*
    This method is offered from the Game to the GameOrchestrator.
    I think that during this phase we should ask the players what totem they want and where to place
    the totem on the starting tile.
     */

    /**
     * Performs the initial game setup and produces the first public state snapshot.
     *
     * <p>The setup builds the offer path on the board, fills the visible card rows, initializes the
     * round order from the player list, and transitions the game from {@code SETUP} to
     * {@code IN_PROGRESS}.
     *
     * @return an action result describing the freshly initialized match state
     */
    public ActionResult setup(){
        if (this.status != GameStatus.SETUP) {
            throw new InvalidGameStatusException("setup", this.status);
        }
        //Operation to perform here:
        //Place people on the map and build the offer path
        this.board.buildOfferPath();

        //Initialize card upper row and lower row
        this.board.setupInitialRows();

        //Now I have to update the turnordertrack fsm to it knows the current round order
        this.turnOrderTrack.setNewOrder(players);

        //Note : Turnstate it's already in waiting for totem move state
        //Need to return an ActionrResult object

        //I can update the game status
        this.status = GameStatus.IN_PROGRESS;
        this.currentRoundPhase = RoundPhase.PLAYER_TURNS;
        return buildActionResult(false, false, false);
    }

    //The game orchestrator redirect to me the move from the player in a specific location

    /**
     * Applies the current player's totem move to the requested offer-space tile.
     *
     * <p>The method validates turn ownership, checks that the turn phase allows moving the totem,
     * verifies that the destination space is available, places the player on the board, and updates
     * the turn-state machine with the number of cards the player is allowed to draw from each row.
     *
     * @param player player attempting the move
     * @param tilecode code of the destination tile on the offer path
     * @return an action result describing the updated state after the move
     */
    public ActionResult moveTotem(Player player, String tilecode){

        if(this.status !=  GameStatus.IN_PROGRESS) {throw new InvalidGameStatusException("moveTotem", this.status);}

        if(this.currentRoundPhase !=  RoundPhase.PLAYER_TURNS) {throw  new InvalidGamePhaseException("moveTotem", this.currentRoundPhase);}

        //Firstly I need to check if the player can make that request
        if(!turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
        }
        //Then I check if he can move the totem
        if(!turnState.canMoveTotem()){
            throw new InvalidTurnPhaseException("moveTotem", this.turnState.getPhase());
        }

        //I ask the board if a specific tile is free
        if(tilecode == null || !board.isSpaceAvailable(tilecode)){throw new TileNotAvailableException(tilecode);}

        //Place the totem on the specific tile, i get the card he can get from the upper row and the lower row
        //int [] choices = board.placePlayerOnOfferSpace(player, tilecode);

        //Update the turnfsmle
        //turnState.onTotemMoved(choices[0], choices[1]);

        //Once the totem has been moved I can update the turnstate
        //I should pass the player because he has a tribe and maybe that tribe has a perk on the drawnable cards


        return buildActionResult(false, false, false);
    }

    //Return the board state

    /**
     * Returns a snapshot of the current board rows without exposing draftable-card suggestions.
     *
     * @return an action result that includes only the visible board cards
     */
    public ActionResult showBoardCard(){
        return buildBoardSnapshotActionResult(false, false, false);
    }
    //Ricevo player, valido se la mossa è accettabile,
    //Chiamo mostrami carte pescabili sulla board passando il player

    /**
     * Returns the list of cards the current player is currently allowed to draft.
     *
     * @return an action result that includes only the drawable upper and lower cards
     */
    public ActionResult showDrawableCard(){
        return buildDrawableCardsActionResult(false, false, false);
    }

    //TODO:
    //Vedere metodo di pescaggio aggiuntivo dopo gli eventi, avviene pescaggio dopo essere tornato in posizione
    //fa scelta solo da sopra

    //Which card on the board the player choose to pick

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
     * @return an action result describing the state after the card choice is applied
     * @throws InvalidGameStatusException if the game is not in {@code IN_PROGRESS}
     * @throws InvalidGamePhaseException if the round is not in {@code PLAYER_TURNS}
     * @throws NotPlayersTurnException if {@code player} is not the active player
     * @throws InvalidTurnPhaseException if the turn state is not waiting for a card selection
     * @throws CardNotPresentException if the selected card is not currently on the board
     * @throws CardNotDrawableException if the selected card cannot be drafted by {@code player}
     * @throws CardRowSelectionNotAllowedException if the selected row is not currently selectable
     */
    public ActionResult chooseCard(Player player, Card card){

        if(this.status !=  GameStatus.IN_PROGRESS) {throw new InvalidGameStatusException("chooseCard", this.status);}

        if(this.currentRoundPhase !=  RoundPhase.PLAYER_TURNS) {throw  new InvalidGamePhaseException("chooseCard",this.currentRoundPhase);}

        //Check if the player is correct
        if(!turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(this.turnOrderTrack.getCurrentPlayer(), player);
        }
        //Then I check if he can drawn card
        if (!turnState.isWaitingForCardSelection()) {
            throw new InvalidTurnPhaseException("chooseCard", turnState.getPhase());
        }

        //Check if he can draw
        CardRow cardRow = board.getCardRow(card);
        if(cardRow == CardRow.NOT_PRESENT){
            throw  new CardNotPresentException(card);
        }
        if (!board.canPlayerDrawCard(player, card)) {
            throw new CardNotDrawableException(player, card);
        }

        if (cardRow == CardRow.UPPER) {
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

        //Now I have to check If I have to advance to another player

        if (turnState.isTurnCompleted()) {
            if(turnOrderTrack.isLastPlayer()){
                //I need to go in a state in which only event resolution in possible
                this.currentRoundPhase = RoundPhase.RESOLVING_EVENTS;
                turnState.resetForNextPlayer();
            }else {
                turnOrderTrack.advanceToNextPlayer();
                turnState.resetForNextPlayer();
            }

        }

        return buildActionResult(false, false, false);
    }


    //When the game is ended, I need to calculate the score in terms of PP for every player, I have to return
    //something that can be used both for showing the result and also to save in the leaderboard



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
        //Risoluzione eventi
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

        List<Player> newOrder = this.board.rebuildOrderFromOfferPath();
        this.turnOrderTrack.setNewOrder(newOrder);
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

        //Shifting cards + Refilling card + if needed shifting building and refilling building
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

        //Se un player ha il building con il perk può fare una pescata (magari è solo una riscriuttra del choosecard
        //Con check iniziali diversi

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




    /*

    -----HELPER------

     */

    /**
     * Builds a complete action snapshot including board rows and the current player's drawable
     * cards.
     *
     * @param turnEnded whether the triggering action ended the current turn
     * @param roundEnded whether the triggering action ended the current round
     * @param gameEnded whether the triggering action ended the game
     * @return full action snapshot for the orchestrator
     */
    private ActionResult buildActionResult(boolean turnEnded, boolean roundEnded, boolean gameEnded) {
        return new ActionResult(
                this.turnState.getPhase(),
                this.turnOrderTrack.getCurrentPlayer(),
                this.status,
                this.currentEra,
                this.turnOrderTrack.getRoundNumber(),
                turnEnded,
                roundEnded,
                gameEnded,
                this.board.getUpperRowCards(),
                this.board.getLowerRowCards(),
                getDrawableUpperCardsForCurrentPlayer(),
                getDrawableLowerCardsForCurrentPlayer()
        );
    }

    /**
     * Builds an action snapshot focused on the visible board rows only.
     *
     * @param turnEnded whether the triggering action ended the current turn
     * @param roundEnded whether the triggering action ended the current round
     * @param gameEnded whether the triggering action ended the game
     * @return board-only snapshot for the orchestrator
     */
    private ActionResult buildBoardSnapshotActionResult(boolean turnEnded, boolean roundEnded, boolean gameEnded) {
        return new ActionResult(
                this.turnState.getPhase(),
                this.turnOrderTrack.getCurrentPlayer(),
                this.status,
                this.currentEra,
                this.turnOrderTrack.getRoundNumber(),
                turnEnded,
                roundEnded,
                gameEnded,
                this.board.getUpperRowCards(),
                this.board.getLowerRowCards(),
                List.of(),
                List.of()
        );
    }

    /**
     * Builds an action snapshot focused on the cards drawable by the current player.
     *
     * @param turnEnded whether the triggering action ended the current turn
     * @param roundEnded whether the triggering action ended the current round
     * @param gameEnded whether the triggering action ended the game
     * @return drawable-card snapshot for the orchestrator
     */
    private ActionResult buildDrawableCardsActionResult(boolean turnEnded, boolean roundEnded, boolean gameEnded) {
        return new ActionResult(
                this.turnState.getPhase(),
                this.turnOrderTrack.getCurrentPlayer(),
                this.status,
                this.currentEra,
                this.turnOrderTrack.getRoundNumber(),
                turnEnded,
                roundEnded,
                gameEnded,
                List.of(),
                List.of(),
                getDrawableUpperCardsForCurrentPlayer(),
                getDrawableLowerCardsForCurrentPlayer()
        );
    }

    /**
     * Computes the upper-row cards currently drawable by the active player.
     *
     * @return list of upper-row cards the current player may draft
     */
    private List<Card> getDrawableUpperCardsForCurrentPlayer() {
        Player currentPlayer = this.turnOrderTrack.getCurrentPlayer();
        return this.board.getDraftableUpperCards(currentPlayer);
    }

    /**
     * Computes the lower-row cards currently drawable by the active player.
     *
     * @return list of lower-row cards the current player may draft
     */
    private List<Card> getDrawableLowerCardsForCurrentPlayer() {
        Player currentPlayer = this.turnOrderTrack.getCurrentPlayer();
        return this.board.getDraftableLowerCards(currentPlayer);
    }

}
