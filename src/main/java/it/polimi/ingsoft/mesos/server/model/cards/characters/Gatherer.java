package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

/**
 * Character card representing a gatherer.
 */
public class Gatherer extends CharacterCard {
    /**
     * Creates a gatherer card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type
     */
    public Gatherer(int id, Era era, TribeCardType type) {
        super(id, era, type);
    }
}
