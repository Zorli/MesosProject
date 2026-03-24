package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.controller.player.Player;

/**
 * Base class for all tribe-deck event cards.
 *
 * <p>Event cards implement {@link EventAction} and are never drawable by players directly from the
 * board, because they are resolved automatically as part of round progression.
 */
public abstract class EventCard extends TribeCard implements EventAction {
    /**
     * Creates an event card with the provided identifier, era, and type.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type, expected to be {@code EVENT}
     */
    public EventCard(int id, Era era, TribeCardType type) { super(id, era, type);}

    /**
     * Returns whether the event card can be drawn by the given player.
     *
     * @param player player attempting to draw the card
     * @return always {@code false} because event cards are resolved automatically
     */
    @Override
    public boolean isDrawable(Player player) {
        return false;
    }
}
