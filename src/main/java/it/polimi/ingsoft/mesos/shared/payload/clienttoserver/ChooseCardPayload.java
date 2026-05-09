package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;

import java.io.Serializable;
public class ChooseCardPayload implements Serializable,ClientPayload {
   public final int cardId;
    public ChooseCardPayload(int cardId) {
        this.cardId = cardId;
    }
}
