package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;


/*
    This class is meant to be the FSM that check every step in the single player turn.
*/

/**
 * Finite-state machine that validates and tracks the phases of a single player's turn.
 *
 * <p>The state starts waiting for a totem move, transitions to card selection once the player is
 * positioned on the offer path, tracks how many upper and lower row choices remain, and marks the
 * turn as completed when all mandatory selections have been consumed.
 */
public class TurnState {
    private TurnPhase phase;
    private Player activePlayer;
}
