package it.polimi.ingsoft.mesos.server.model.game.cards.characters;

import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

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
