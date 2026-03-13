package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

public class CavePaintingEventCard extends EventCard {
    private final int limitForPositive;
    private final int prestigePointsForPositive;
    private final int prestigePointsForNegative;

    public CavePaintingEventCard(int id, Era era, int limitForPositive, int prestigePointsForPositive, int prestigePointsForNegative) {
        super(id, era);
        this.limitForPositive = limitForPositive;
        this.prestigePointsForPositive = prestigePointsForPositive;
        this.prestigePointsForNegative = prestigePointsForNegative;
    }

    @Override
    public void applyEffect(ArrayList<Player> players) {
        for (Player player : players) {
            int numOfArtists = player.getTribe().countArtists();
            if (numOfArtists < this.limitForPositive) {
                player.payPrestigePoints(this.prestigePointsForNegative);
            } else {
                player.gainPrestigePoints(this.prestigePointsForPositive * numOfArtists);
            }
        }
    }


}
