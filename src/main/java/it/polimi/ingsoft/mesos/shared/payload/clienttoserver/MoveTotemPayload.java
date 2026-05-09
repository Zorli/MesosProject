package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;
import java.io.Serializable;
public class MoveTotemPayload implements Serializable, GameClientPayload {
    public final String tile;
    public MoveTotemPayload(String tile) {
        this.tile = tile;
    }
}
