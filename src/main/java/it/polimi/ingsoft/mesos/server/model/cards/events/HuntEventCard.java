package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

public class HuntEventCard extends EventCard {
    private final int foodReward;
    private final int prestigePointsReward;

    public HuntEventCard(int id, Era era, int foodReward, int prestigePointsReward) {
        super(id, era);
        this.foodReward = foodReward;
        this.prestigePointsReward = prestigePointsReward;
    }

    @Override
    public void applyEffect(ArrayList<Player> players) {
        for (Player player : players) {
            int hunterCount = player.getTribe().countHunters();
            player.gainPrestigePoints(this.prestigePointsReward * hunterCount);
            player.gainFood(this.foodReward * hunterCount);
        }
    }
}
