package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class HuntEventCard extends TribeCard {
    private final int foodReward;
    private final int prestigePointsReward;

    public HuntEventCard(int id, Era era, int foodReward, int prestigePointsReward) {
        super(id, era);
        this.foodReward = foodReward;
        this.prestigePointsReward = prestigePointsReward;
    }

    public void applyEffect(Game game) {
        for (Player player : game.getPlayers()) {
            int hunterCount = player.getTribe().countHunters();
            player.gainPrestigePoints(this.prestigePointsReward * hunterCount);
            player.gainFood(this.foodReward * hunterCount);
        }

    }

}
