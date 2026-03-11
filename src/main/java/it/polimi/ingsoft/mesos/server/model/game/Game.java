package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

public class Game {
    private final int id;
    private ArrayList<Player> players;
    private Era currentEra;
    private GameStatus status;

    public Game(int id, ArrayList<Player> players, Era era, GameStatus status) {
        this.id = id;
        this.players = players;
        this.currentEra = era;
        this.status = GameStatus.SETUP;
    }

    public void setStatus(GameStatus newstatus){
        this.status = newstatus;
    }

    public ArrayList<Player> getPlayers(){
        return this.players;
    }

}
