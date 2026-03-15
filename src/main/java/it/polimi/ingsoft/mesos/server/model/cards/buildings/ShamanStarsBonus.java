package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

/**
 * Building that increases the amount of shaman stars considered by the
 * shaman-ritual related effects.
 */
public class ShamanStarsBonus extends BuildingCard {
    private final int starsBonus;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param benefitType effect represented by the building
     * @param starsBonus additional stars granted by the building
     */
    public ShamanStarsBonus(int id, Era era, int fc, int pp, BenefitType benefitType, int starsBonus) {
        super(id, era, fc, pp, benefitType);
        this.starsBonus = starsBonus;
    }

    /**
     * Returns the additional stars granted by the building.
     *
     * @return shaman star bonus
     */
    public int getStarsBonus() {
        return this.starsBonus;
    }
}
