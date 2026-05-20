package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game;

import java.io.Serializable;
/**
 * Payload sent by a player to request performing an extra draw action.
 * <p>
 * This request occurs when a player qualifies for drawing additional cards
 * as an effect of previously played cards or specific tribe mechanics.
 */
public class ExtraDrawPayload implements Serializable, GameClientPayload {
    /** The unique identifier of the card to draw as an extra card. */
    public final int cardId;

    /**
     * Constructs a new ExtraDrawPayload.
     *
     * @param cardId the identifier of the card to draw extra
     */
    public ExtraDrawPayload(int cardId) {
        this.cardId = cardId;
    }
}
