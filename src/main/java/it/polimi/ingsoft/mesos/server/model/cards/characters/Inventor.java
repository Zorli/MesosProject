package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

/**
 * Character card representing an inventor associated with a specific invention type.
 */
public class Inventor extends CharacterCard {
    private final InventionType inventionType;

    /**
     * Creates an inventor card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type
     * @param inventionType invention symbol carried by the inventor
     */
    public Inventor(int id, Era era, TribeCardType type, InventionType inventionType) {
        super(id, era, type);
        this.inventionType = inventionType;
    }

    /**
     * Returns the invention type associated with this inventor.
     *
     * @return inventor invention type
     */
    public InventionType getInventionType() {
        return this.inventionType;
    }
}
