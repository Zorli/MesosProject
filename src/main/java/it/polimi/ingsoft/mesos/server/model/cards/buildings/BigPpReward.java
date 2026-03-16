package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Building that grants an additional flat prestige-point reward during final scoring.
 */
public class BigPpReward extends BuildingCard {
    private final int ppReward;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param type effect represented by the building
     * @param ppReward extra prestige points granted by the effect
     */
    public BigPpReward(int id, Era era, int fc, int pp, BenefitType type, int ppReward) {
        super(id, era, fc, pp, type);
        this.ppReward = ppReward;
    }

    /**
     * Applies the additional prestige-point reward to the given player.
     *
     * @param player player receiving the reward
     */
    public void applyEffect(Player player) {
        player.gainPrestigePoints(this.ppReward);
    }
}
