package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

/**
 * Marker interface for all client-to-preLobby payloads related to the active game phase.
 * <p>
 * Implementing classes carry data for in-game actions performed by players
 * during their turns, such as totem movement or card drafting decisions.
 */
public interface GameClientPayload extends ClientPayload {
}
