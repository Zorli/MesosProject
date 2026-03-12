package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

//This class is meant to be used by the game orchestrator, in order to decouple the phase in which all the model is
//instantied based on the number of players, from the phase in which the model is used to apply the game logic.
//This is an application of the FactoryMethod
public class DefaultGameFactory implements GameFactory {

    @Override
    public Game createGame(ArrayList<Player> players) {
        //Based on the numbers of players here i have to create all the classe needed for the game, I am going
        //to rely on specifical setup methods offered by the package manager classes.

        //I need to understand for every package which is the responsible class, or if i need to instantiate them
        //directly here.
        return null;
    }
}
