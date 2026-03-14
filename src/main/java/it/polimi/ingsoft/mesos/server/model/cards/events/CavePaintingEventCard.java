package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

public class CavePaintingEventCard extends EventCard {
    private final int limitForPositive;
    private final int prestigePointsForPositive;
    private final int prestigePointsForNegative;

    public CavePaintingEventCard(int id, Era era, int limitForPositive, TribeCardType type, int prestigePointsForPositive, int prestigePointsForNegative) {
        super(id, era, type);
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

            //TODO: bonus di cibo se si ha l'edificio che fa guadagnare cibo per ogni artista durante l'evento

        }
    }


}
