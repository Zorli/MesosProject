package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class SustenanceEventCard extends TribeCard {
    public SustenanceEventCard(int id, Era era) {
        super(id, era);
    }

    // TODO: nei param il game perché devo fare foreach player in game

    public void applyEffect(Player player){
        int numOfGatherers = player.getTribe().countGatherers();
        int numOfCharacters = player.getTribe().getCharactersSize();
        int totalToRemove = numOfCharacters - numOfGatherers * 3;
        if(totalToRemove < 0){
            totalToRemove = 0;
        }
        player.payFood(totalToRemove);
    }
}
