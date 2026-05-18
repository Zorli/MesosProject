package it.polimi.ingsoft.mesos.server.model.game.game.exception;

public class InvalidConnectionState extends IllegalStateException{
    public InvalidConnectionState(String message) {
        super("Invalid connection state: " + message);
    }
}
