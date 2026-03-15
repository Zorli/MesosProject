package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;

import java.util.ArrayList;

//References : docs/design-doc-game-state.md

public class Game {
    private final int id;   //Immutable, provided by the game orchestrator
    private final ArrayList<Player> players;
    private Era currentEra; //Mutable
    private GameStatus status;  //Mutable
    private TribeDeck tribeDeck; //Should I keep the reference to the deck directly, or I only need the board?
    private BuildingDeck buildingDeck;
    private Board board;
    private TurnOrderTrack turnOrderTrack;
    private TurnState turnState;
    //Need only the id and the number of players, the era it's clearly the first one
    public Game(int id, ArrayList<Player> players, TurnState turnState, TurnOrderTrack turnOrderTrack, Board board,
                TribeDeck  tribeDeck, BuildingDeck buildingDeck)
    {
        this.id = id;
        this.players = players;
        this.currentEra = Era.first;
        this.status = GameStatus.SETUP;
    }

    //Setter
    public void setCurrentEra(Era currentEra) {
        this.currentEra = currentEra;
    }
    public void setStatus(GameStatus newstatus){
        this.status = newstatus;
    }

    //Getters
    public ArrayList<Player> getPlayers(){
        return this.players;
    }
    public int  getId(){
        return this.id;
    }
    public Era getCurrentEra(){
        return this.currentEra;
    }
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
    public void setup(){
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
    }

    //The game orchestrator redirect to me the move from the player in a specific location
    public void moveTotem(Player player, ){
        //Firstly I need to check if the player can make that request
        if(!turnOrderTrack.getCurrentPlayer().equals(player)) {
            throw new IllegalStateException("It's not the turn of this player");
        }
        //Then I check if he can move the totem
        if(!turnState.canMoveTotem()){throw new IllegalArgumentException("The player has already moved the totem");}

        //I can as the board to move a specific totem
        //Here I need the api to ask to the board to make the specific

        //Chiamato checck metti in posizione totem su casellax booelan

        //Se può faccio place , mi vengono ritornare quante carte da sopra e da sotto.

        //Once the totem has been moved I can update the turnstate
        //I should pass the player because he has a tribe and maybe that tribe has a perk on the drawnable cards
        turnState.onTotemMoved(player);
    }

    //Return the board state
    public void showBoardCard(){


    }
    //Ricevo player, valido se la mossa è accettabile,
    //Chiamo mostrami carte pescabili sulla board passando il player
    public showDrawableCard(){
        //Mi passi due liste e le salvo
    }

    //TODO:
    //Vedere metodo di pescaggio aggiuntivo dopo gli eventi, avviene pescaggio dopo essere tornato in posizione
    //fa scelta solo da sopra



    //Which card on the board the player choose to pick
    public void chooseCard(Player player, Card card){
        //COntrollo se è sopra o sotto e faccio la richiesta alla board.
        //Faccio i check

    }


    //When the game is ended, I need to calculate the score in terms of PP for every player, I have to return
    //something that can be used both for showing the result and also to save in the leaderboard
    public void calculateFinalScore(){



    }

    private endRound(){
        //Risoluzione eventi
        //Ritorno al posto
        //Eventuale pescaggio post ritorno al posto
    }

    private

    //Se sono arrivato al round 10 e ho tuti hanno mosso faccio la sola risoluzione degli eventi,
    //Notifico e posso chiamare il conteggio dei punti
    private void checkGameFinished(){


    }

}
