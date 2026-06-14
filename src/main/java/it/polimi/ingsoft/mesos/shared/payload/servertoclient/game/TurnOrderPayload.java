package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
import java.util.List;

/**
 * Represents the sequence/turn order of players for a game round.
 * <p>
 * This includes the round number, active round phase, the nickname of the player
 * whose turn is active, and the full ordered list of player nicknames.
 */
public class TurnOrderPayload implements Serializable {
    /** The current round number index. */
    public final int roundNumber;

    /** The current active phase of the round, as a {@code RoundPhase} name (e.g. PLACING_TOTEMS, PLAYER_DRAWS, EXTRA_DRAWS). */
    public final String currentRoundPhase;

    /** The nickname of the player who must perform the next action. */
    public final String currentPlayerNickname;

    /** The list of player nicknames ordered by their turn sequence. */
    public final List<String> turnOrder;

    /**
     * Constructs a new TurnOrderPayload.
     *
     * @param roundNumber current round index
     * @param currentRoundPhase current round phase name
     * @param currentPlayerNickname active player's nickname
     * @param turnOrder ordered list of player nicknames
     */
    public TurnOrderPayload(
            int roundNumber,
            String currentRoundPhase,
            String currentPlayerNickname,
            List<String> turnOrder
    ) {
        this.roundNumber = roundNumber;
        this.currentRoundPhase = currentRoundPhase;
        this.currentPlayerNickname = currentPlayerNickname;
        this.turnOrder = turnOrder;
    }
}
