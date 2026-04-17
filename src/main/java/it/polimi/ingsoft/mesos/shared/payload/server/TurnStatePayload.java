package it.polimi.ingsoft.mesos.shared.payload.server;
import java.io.Serializable;
public class TurnStatePayload implements Serializable {
    public final String phase;

    public TurnStatePayload(String phase) {
        this.phase = phase;
    }
}
