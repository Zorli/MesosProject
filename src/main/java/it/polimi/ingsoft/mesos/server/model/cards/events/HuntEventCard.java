package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
 import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class HuntEventCard extends TribeCard {
    private int foodReward;
    private int prestigePointsReward;

    public HuntEventCard(int id, Era era, int foodReward, int prestigePointsReward) {
        super(id, era);
        this.foodReward = foodReward;
        this.prestigePointsReward = prestigePointsReward;
    }
    
    public void applyEffect(Player player) {
        int hunterCount = player.getTribe().countHunters();
        player.gainPrestigePoints(prestigePointsReward * hunterCount);
        player.gainFood(foodReward * hunterCount);
    }

}
