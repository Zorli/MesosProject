package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;

import java.io.Serializable;

/**
 * Payload sent by an authenticated client to request creating a new lobby room.
 * <p>
 * If creation succeeds, the requester becomes the host of the new lobby
 * and is automatically added as its first member player with the selected totem color.
 */
public class CreateLobbyPayload implements Serializable, PreLobbyClientPayload {
    /** The maximum capacity (number of players) for the new lobby. */
    public final int chosenCapacity;
    /** The totem color selected by the lobby host. */
    public final TotemColor totemColor;

    /**
     * Constructs a new CreateLobbyPayload.
     *
     * @param chosenCapacity the maximum capacity of the lobby to create
     * @param totemColor the totem color selected by the host; the server model rejects null values
     */
    public CreateLobbyPayload(int chosenCapacity, TotemColor totemColor) {
        this.chosenCapacity = chosenCapacity;
        this.totemColor = totemColor;
    }
}
