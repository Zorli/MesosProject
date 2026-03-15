package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Building that augments the hunt event with additional food and prestige
 * points for each hunter in the tribe.
 */
public class HunterBonus extends BuildingCard {
    private final int bonusFoodPerHunter;
    private final int bonusPrestigePointsPerHunter;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param type effect represented by the building
     * @param bonusFoodPerHunter extra food granted per hunter during the event
     * @param bonusPrestigePointsPerHunter extra prestige points granted per hunter during the event
     */
    public HunterBonus(int id, Era era, int fc, int pp, BenefitType type, int bonusFoodPerHunter, int bonusPrestigePointsPerHunter) {
        super(id, era, fc, pp, type);
        this.bonusFoodPerHunter = bonusFoodPerHunter;
        this.bonusPrestigePointsPerHunter = bonusPrestigePointsPerHunter;
    }

    /**
     * Applies the hunt-event bonus to the given player.
     *
     * @param player player receiving the bonus
     */
    public void applyBonusHunterEvent(Player player) {
        int count = player.getTribe().countHunters();
        player.gainFood(count * this.bonusFoodPerHunter);
        player.gainPrestigePoints(count * this.bonusPrestigePointsPerHunter);
    }

    /**
     * Returns the extra food granted for each hunter.
     *
     * @return bonus food per hunter
     */
    public int getBonusFoodPerHunter() {
        return this.bonusFoodPerHunter;
    }

    /**
     * Returns the extra prestige points granted for each hunter.
     *
     * @return bonus prestige points per hunter
     */
    public int getBonusPrestigePointsPerHunter() {
        return this.bonusPrestigePointsPerHunter;
    }
}
