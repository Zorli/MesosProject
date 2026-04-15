package it.polimi.ingsoft.mesos.shared.payload.client;

public class CreateLobbyPayload {
    public final int chosenCapacity;

    public CreateLobbyPayload(int chosenCapacity) {
        this.chosenCapacity = chosenCapacity;
    }
}
