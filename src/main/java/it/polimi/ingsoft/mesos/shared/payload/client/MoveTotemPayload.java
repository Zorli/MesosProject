package it.polimi.ingsoft.mesos.shared.payload.client;
import java.io.Serializable;
public class MoveTotemPayload implements Serializable,ClientPayload {
    public final String tile;
    public MoveTotemPayload(String tile) {
        this.tile = tile;
    }
}
