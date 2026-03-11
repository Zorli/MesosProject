package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class ShamanRitualEventCard extends TribeCard{
    private final int prestigePointsForHigher;
    private final int prestigePointsForLower;
    public ShamanRitualEventCard(int id, Era era, int prestigePointsForHigher, int prestigePointsForLower) {
        super(id, era);
        this.prestigePointsForHigher = prestigePointsForHigher;
        this.prestigePointsForLower = prestigePointsForLower;
    }

    // TODO: nei param il game perché devo fare foreach player in game

    public void applyEffect() {

        //int shamanCount = player.getTribe().countShamans();
    }

}
