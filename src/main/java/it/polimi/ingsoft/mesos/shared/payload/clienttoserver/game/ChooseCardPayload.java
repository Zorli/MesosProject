package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.GameClientPayload;

import java.io.Serializable;
public class ChooseCardPayload implements Serializable, GameClientPayload {
   public final int cardId;
    public ChooseCardPayload(int cardId) {
        this.cardId = cardId;
    }
}
