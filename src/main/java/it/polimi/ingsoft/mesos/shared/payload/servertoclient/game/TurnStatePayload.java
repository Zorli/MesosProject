package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
/**
 * Represents the state of the active player's standard card-drafting turn.
 * <p>
 * This indicates only whether the current card-drafting turn is awaiting a
 * card selection or has been completed; it is narrower than the overall round phase.
 */
public class TurnStatePayload implements Serializable {
    /** The card-drafting turn state, as a {@code DraftTurnState} name (TURN_COMPLETED or WAITING_FOR_CARD_SELECTION). */
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
