package it.polimi.ingsoft.mesos.server.model.game.cards;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

/**
 * Common behavior shared by all card types.
 *
 * <p>The interface exposes the minimal information needed by the board and the rest of the model
 * to order cards by era and determine whether a specific player may currently draw them.
 */
public interface CardAction {
    /**
     * Returns the era associated with the card.
     *
     * @return card era
     */
    Era getEra();

    /**
     * Checks whether the provided player may draw the card at the current time.
     *
     * @param player player attempting to draw the card
     * @return {@code true} when the card is drawable by that player
     */
    boolean isDrawable(Player player);
}
