package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;

import java.util.ArrayList;

public class SustenanceEventCard extends TribeCard implements EventAction {
    public SustenanceEventCard(int id, Era era) {
        super(id, era);
    }

    @Override
    public void applyEffect(ArrayList<Player> players) {
        for (Player player : players) {
            int numOfGatherers = player.getTribe().countGatherers();
            int numOfCharacters = player.getTribe().getCharactersSize();
            int totalToRemove = numOfCharacters - numOfGatherers * 3;
            if (totalToRemove < 0) {
                totalToRemove = 0;
            }
            player.payFood(totalToRemove);
        }

    }
}
