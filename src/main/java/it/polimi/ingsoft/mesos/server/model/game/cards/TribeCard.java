package it.polimi.ingsoft.mesos.server.model.game.cards;

import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

/**
 * Base class for every card contained in the tribe deck.
 *
 * <p>In addition to the generic card fields, tribe cards expose their logical type so that the
 * model can distinguish between characters and events and apply type-specific rules.
 */
public abstract class TribeCard extends Card {
    private final TribeCardType type;

    /**
     * Creates a tribe card with the provided identifier, era, and logical type.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type
     */
    public TribeCard(int id, Era era , TribeCardType type) {
        super(id, era);
        this.type = type;
    }

    /**
     * Returns the logical type of the tribe card.
     *
     * @return tribe-card type
     */
    public TribeCardType getType(){
        return this.type;
    }

    @Override
    public String getCardType() {
        return this.type.name();
    }
}
