package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
/**
 * Represents the current status/phase of a player's turn.
 * <p>
 * This defines whether the player is currently moving their totem,
 * drafting cards, resolving card effects, or drawing extra cards.
 */
public class TurnStatePayload implements Serializable {
    /** The current active sub-phase of the player's turn (e.g. TOTEM_MOVEMENT, CARD_DRAFT, EXTRA_DRAW). */
    public final String phase;

    /**
     * Constructs a new TurnStatePayload.
     *
     * @param phase the current active turn phase
     */
    public TurnStatePayload(String phase) {
        this.phase = phase;
    }
}
