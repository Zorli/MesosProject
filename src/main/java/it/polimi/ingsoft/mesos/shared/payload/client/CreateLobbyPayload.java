package it.polimi.ingsoft.mesos.shared.payload.client;
import java.io.Serializable;
public class CreateLobbyPayload implements Serializable {
    public final int chosenCapacity;

    public CreateLobbyPayload(int chosenCapacity) {
        this.chosenCapacity = chosenCapacity;
    }
}
