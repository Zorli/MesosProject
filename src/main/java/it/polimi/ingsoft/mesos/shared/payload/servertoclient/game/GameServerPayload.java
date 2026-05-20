package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

/**
 * Marker interface for all server-to-client payloads related to the active game phase.
 * <p>
 * Implementing classes carry data representing the updated state of the game,
 * player boards, card drafts, turn changes, or scoreboard summaries.
 */
public interface GameServerPayload extends ServerPayload {
}
