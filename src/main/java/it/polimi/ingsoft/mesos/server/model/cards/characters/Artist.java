package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

/**
 * Character card representing an artist.
 */
public class Artist extends CharacterCard {
    /**
     * Creates an artist card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type
     */
    public Artist (int id, Era era, TribeCardType type) {
        super(id, era, type);
    }
}
