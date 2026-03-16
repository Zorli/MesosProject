package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class BigPpReward extends BuildingCard {
    private final int ppReward;

    public BigPpReward(int id, Era era, int fc, int pp, BenefitType type, int ppReward) {
        super(id, era, fc, pp, type);
        this.ppReward = ppReward;
    }

    public void applyEffect(Player player) {
        player.gainPrestigePoints(this.ppReward);
    }
}
