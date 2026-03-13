package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

//This class is meant to be used by the game orchestrator, in order to decouple the phase in which all the model is
//created based on the number of players, from the phase in which the model is used to apply the game logic.
//This is an application of the FactoryMethod


public class DefaultGameFactory implements GameFactory {
    private int nextGameId = 0;

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
        1) TurnState
        2) Turn Oder Track
        3) Card Package
        4) BoardPackage Creo oggetto board, poi mi espone già il costruttore






         */

        return new Game(nextGameId++, players);


    }
}
