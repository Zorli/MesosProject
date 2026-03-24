package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.controller.player.Player;

/**
 * Building that grants additional food during the cave painting event based on
 * the number of artists in the tribe.
 */
public class CavePaintingBonus extends BuildingCard {
    private final int bonusFoodPerArtist;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param type effect represented by the building
     * @param bonusFoodPerArtist food gained for each artist during the event
     */
    public CavePaintingBonus(int id, Era era, int fc, int pp, BenefitType type, int bonusFoodPerArtist) {
        super(id, era, fc, pp, type);
        this.bonusFoodPerArtist = bonusFoodPerArtist;
    }

    /**
     * Applies the event bonus to the given player.
     *
     * @param player player receiving the bonus
     */
    public void applyBonusCavePaintingEvent(Player player) {
        int count = player.getTribe().countArtists();
        player.gainFood(count * this.bonusFoodPerArtist);
    }

    /**
     * Returns the food granted for each artist.
     *
     * @return bonus food per artist
     */
    public int getBonusFoodPerArtist() {
        return this.bonusFoodPerArtist;
    }
}
