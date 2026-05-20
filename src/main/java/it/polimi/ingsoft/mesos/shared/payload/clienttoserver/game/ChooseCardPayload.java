package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game;

import java.io.Serializable;
/**
 * Payload sent by a player to request drafting/choosing a specific card from the draft pool.
 * <p>
 * The target card must be one of the cards currently available in the offer
 * matching the tile space where the player's totem is located.
 */
public class ChooseCardPayload implements Serializable, GameClientPayload {
    /** The unique identifier of the card to draft. */
    public final int cardId;

    /**
     * Constructs a new ChooseCardPayload.
     *
     * @param cardId the identifier of the chosen card
     */
    public ChooseCardPayload(int cardId) {
        this.cardId = cardId;
    }
}
