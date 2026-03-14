package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class HunterBonus extends BuildingCard {
    private final int bonusFoodPerHunter;
    private final int bonusPrestigePointsPerHunter;

    public HunterBonus(int id, Era era, int fc, int pp, BenefitType type, int bonusFoodPerHunter, int bonusPrestigePointsPerHunter) {
        super(id, era, fc, pp, type);
        this.bonusFoodPerHunter = bonusFoodPerHunter;
        this.bonusPrestigePointsPerHunter = bonusPrestigePointsPerHunter;
    }

    public void applyBonusHunterEvent(Player player) {
        int count = player.getTribe().countHunters();
        player.gainFood(count * this.bonusFoodPerHunter);
        player.gainPrestigePoints(count * this.bonusPrestigePointsPerHunter);
    }
}
