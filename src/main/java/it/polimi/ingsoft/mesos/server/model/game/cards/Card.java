package it.polimi.ingsoft.mesos.server.model.game.cards;

import it.polimi.ingsoft.mesos.server.model.game.enums.Era;

/**
 * Shared base class for every card in the model.
 *
 * <p>Each card has a stable identifier and belongs to one era. Concrete subclasses provide the
 * rules that determine whether the card can be drawn and what gameplay behavior it triggers.
 */
public abstract class Card implements CardAction {
    private final int id;
    private Era era;

    /**
     * Creates a card with the provided identifier and era.
     *
     * @param id unique card identifier
     * @param era era the card belongs to
     */
    public Card(int id, Era era) {
        this.id = id;
        this.era = era;
    }

    /**
     * Returns the unique identifier of the card.
     *
     * @return card identifier
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the era associated with the card.
     *
     * @return card era
     */
    @Override
    public Era getEra() {
        return this.era;
    }

    /**
     * Updates the era associated with the card.
     *
     * @param era new era value
     */
    public void setEra(Era era) {
        this.era = era;
    }
}
