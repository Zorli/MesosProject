package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

//References : docs/design-doc-game-state.md
//Open questions:
//1) Do this class need to instance all the component of the model?

public class Game {
    private final int id;   //Immutable, provided by the game orchestrator
    private final ArrayList<Player> players;
    private Era currentEra; //Mutable
    private GameStatus status;  //Mutable
    private TribeDeck tribeDeck; //Should I keep the reference to the deck directly, or I only need the board?
    private BuildingDeck buildingDeck;
    //Need only the id and the number of players, the era it's clearly the first one
    public Game(int id, ArrayList<Player> players) {
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

    /*
    This method is offered from the Game to the GameOrchestrator.
    I think that during this phase we should ask the players what totem they want and where to place
    the totem on the starting tile.
     */
    public void setup(){

    }

    //The game orchestrator redirect to me the move from the player in a specific location
    public void moveTotem(Player player, int offerSpaceIndex){



    }

    //Which card on the board the player choose to pick
    public void chooseCard(Player player){


    }


    //When the game is ended, I need to calculate the score in terms of PP for every player, I have to return
    //something that can be used both for showing the result and also to save in the leaderboard
    public void calculateFinalScore(){

    }

}
