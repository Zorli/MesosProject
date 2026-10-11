package it.polimi.ingsoft.mesos.server.model.game.cards.characters;

import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

/**
 * Character card representing a shaman that contributes a fixed number of ritual stars.
 */
public class Shaman extends CharacterCard {
    private final int stars;

    /**
     * Creates a shaman card.
     *
     * @param id card identifier
     * @param era card era
     * @param stars number of stars contributed by the shaman
     * @param type tribe-card type
     */
    public Shaman(int id, Era era, int stars, TribeCardType type) {
        super(id, era, type);
        this.stars = stars;
    }

    /**
     * Returns the number of stars contributed by this shaman.
     *
     * @return shaman stars
     */
    public int getStars(){
        return this.stars;
    }
}
