package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

//This class is meant to be used by the game orchestrator, in order to decouple the phase in which all the model is
//created based on the number of players, from the phase in which the model is used to apply the game logic.
//This is an application of the Factory design pattern

//Here I only give life to the game, then in another step the totem are going to be chosen and placed on the starting tile.
public class DefaultGameFactory implements GameFactory {
    private AtomicInteger nextGameId = new AtomicInteger(0);
    private static final DefaultGameFactory instance = new DefaultGameFactory();
    private static final ParserJson parserJson = new ParserJson();

    //Singleton design pattern
    private DefaultGameFactory() {
        this.nextGameId = new AtomicInteger(0);
    }

    public static DefaultGameFactory getInstance() {
        return instance;
    }


    @Override
    public Game createGame(ArrayList<Player> players) {
        //Based on the numbers of players here I have to create all the classe needed for the game, I am going
        //to rely on specifical setup methods offered by the package manager classes.

        //I need to understand for every package which is the responsible class, or if I need to instantiate them
        //directly here.
        //Here I need also to build the unique id of the game, that has to be an integer, maybe we can use an integer
        //that is incremented every time

        /*
        I need to create :
        1) TurnState : Done
        2) Turn Oder Track : Done
        3) Card Package
        4) BoardPackage Creo oggetto board, poi mi espone già il costruttore
         */


        TribeDeck tribeDeck = ParserJson.parseTribeDeck(); //Need the json and the parser before doing that
        BuildingDeck buildingDeck = ParserJson.parseBuildingDeck();//Need the json and the parser

        Board board = new Board(tribeDeck, buildingDeck, players); //Here the board it's already initiliazed
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();

        //return new Game(nextGameId.getAndIncrement(), players, turnState,turnOrderTrack)//, board,tribeDeck, buildingDeck);
        return null;
    }
}
