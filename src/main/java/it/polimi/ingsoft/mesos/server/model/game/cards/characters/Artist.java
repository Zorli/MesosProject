package it.polimi.ingsoft.mesos.server.model.game.cards.characters;

import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

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
