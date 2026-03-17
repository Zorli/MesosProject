package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

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
    public Game(int id, ArrayList<Player> players, TurnState turnState, TurnOrderTrack turnOrderTrack, Board board) {
        this.id = id;
        this.players = players;
        this.turnState = turnState;
        this.turnOrderTrack = turnOrderTrack;
        this.board = board;
        this.currentEra = Era.first;
        this.status = GameStatus.SETUP;
    }

    // Getters

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


    }

    //After everyone has drawned and after everyion has come back to the starting position, if a player has a certain
    //building, he can drawn an extra card
    public void extraDrawn(Player player, Card card){

    }

    //Adter the extradrawn we resolve the effect using the board
    //If wr are at round ten we go for calculatefinalscore
    public void resolveEffects(){


    }

    //We reset the board and prepare the turnordertrack with the new order for the next totem position,
    //if we are
    public void resetBoard(){



    }

    public void calculateFinalScore(){


    }
}
