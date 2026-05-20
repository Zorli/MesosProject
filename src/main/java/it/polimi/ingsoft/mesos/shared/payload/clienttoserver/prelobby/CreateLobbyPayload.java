package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import java.io.Serializable;
/**
 * Payload sent by an authenticated client to request creating a new lobby room.
 * <p>
 * If creation succeeds, the requester becomes the host of the new lobby
 * and is automatically added as its first member player.
 */
public class CreateLobbyPayload implements Serializable, PreLobbyClientPayload {
    /** The maximum capacity (number of players) for the new lobby. */
    public final int chosenCapacity;

    /**
     * Constructs a new CreateLobbyPayload.
     *
     * @param chosenCapacity the maximum capacity of the lobby to create
     */
    public CreateLobbyPayload(int chosenCapacity) {
        this.chosenCapacity = chosenCapacity;
    }
}
