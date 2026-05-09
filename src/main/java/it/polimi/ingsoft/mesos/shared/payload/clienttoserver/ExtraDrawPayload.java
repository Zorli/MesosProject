package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;
import java.io.Serializable;
public class ExtraDrawPayload implements Serializable,ClientPayload {
    public final int cardId;
    public ExtraDrawPayload(int cardId) {
        this.cardId = cardId;
    }
}
