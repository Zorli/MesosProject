package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class CavePaintingEventCard extends TribeCard {
    private final int limitForPositive;
    private final int prestigePointsForPositive;
    private final int prestigePointsForNegative;
    public CavePaintingEventCard(int id, Era era, int limitForPositive, int prestigePointsForPositive, int prestigePointsForNegative) {
        super(id, era);
        this.limitForPositive = limitForPositive;
        this.prestigePointsForPositive = prestigePointsForPositive;
        this.prestigePointsForNegative = prestigePointsForNegative;
    }

    // TODO: nei param il game perché devo fare foreach player in game

     public void applyEffect(Player player) {

        int numOfArtists = player.getTribe().countArtists();
        if(numOfArtists<this.limitForPositive){
            player.payPrestigePoints(this.prestigePointsForNegative);
        } else {
            player.gainPrestigePoints(this.prestigePointsForPositive * numOfArtists);
        }
    }

}
