package it.polimi.ingsoft.mesos.server.model.game.cards.characters;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

/**
 * Base class for all tribe character cards.
 *
 * <p>Character cards are always drawable when visible on the board, so the default implementation
 * of {@link #isDrawable(Player)} simply returns {@code true}.
 */
public abstract class CharacterCard extends TribeCard {
    /**
     * Creates a character card with the provided identifier, era, and type.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type
     */
    public CharacterCard(int id, Era era, TribeCardType type) {
        super(id, era, type);
    }

    /**
     * Returns whether the character card can be drawn by the given player.
     *
     * @param player player attempting to draw the card
     * @return always {@code true} for character cards
     */
    @Override
    public boolean isDrawable(Player player) {
        return true;
    }
}
