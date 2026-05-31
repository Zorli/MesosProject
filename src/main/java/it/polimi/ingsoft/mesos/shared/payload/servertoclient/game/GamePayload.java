package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;

import java.io.Serializable;
import java.util.List;
//Upper class of the payload
/**
 * Represents the complete game state payload broadcasted by the preLobby.
 * <p>
 * This functions as a full state update containing the current game ID, game status,
 * era index, turn order sequence, active player turn phase, players' status,
 * and the state of the central board.
 */
public class GamePayload implements Serializable, GameServerPayload {
    /** The unique identifier of the game match. */
    public final int id;

    /** The status of the game (e.g. SETUP, IN_PROGRESS, FINISHED). */
    public final String status;

    /** The current era index of the game (1, 2, or 3). */
    public final int currentEra;

    /** The sequence, current active player, and phase of turns in the round. */
    public final TurnOrderPayload turnOrder;

    /** The detailed sub-phase of the active player's turn. */
    public final TurnStatePayload turnState;

    /** The list of player state summaries representing everyone in the game. */
    public final List<PlayerPayload> players;

    /** The current state and layouts of the main game board. */
    public final BoardPayload board;

    /**
     * Constructs a new GamePayload.
     *
     * @param id game identifier
     * @param status current game status
     * @param currentEra active era index
     * @param turnOrder current round turn order details
     * @param turnState sub-phase state of active turn
     * @param players list of all player summaries
     * @param board layout state of central board
     */
    public GamePayload(
            int id,
            String status,
            int currentEra,
            TurnOrderPayload turnOrder,
            TurnStatePayload turnState,
            List<PlayerPayload> players,
            BoardPayload board
    ) {
        this.id = id;
        this.status = status;
        this.currentEra = currentEra;
        this.turnOrder = turnOrder;
        this.turnState = turnState;
        this.players = players;
        this.board = board;
    }
}
