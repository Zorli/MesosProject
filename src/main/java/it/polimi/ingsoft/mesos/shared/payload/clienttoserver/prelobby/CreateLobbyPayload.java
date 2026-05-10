package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.PreLobbyClientPayload;
import java.io.Serializable;
public class CreateLobbyPayload implements Serializable, PreLobbyClientPayload {
    public final int chosenCapacity;

    public CreateLobbyPayload(int chosenCapacity) {
        this.chosenCapacity = chosenCapacity;
    }
}
