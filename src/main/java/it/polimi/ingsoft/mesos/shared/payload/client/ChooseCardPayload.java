package it.polimi.ingsoft.mesos.shared.payload.client;

import java.io.Serializable;
public class ChooseCardPayload implements Serializable {
   public final int cardId;
    public ChooseCardPayload(int cardId) {
        this.cardId = cardId;
    }
}
