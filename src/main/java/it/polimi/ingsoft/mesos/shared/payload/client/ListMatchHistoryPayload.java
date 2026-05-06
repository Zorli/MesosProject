package it.polimi.ingsoft.mesos.shared.payload.client;

import java.io.Serializable;

public class ListMatchHistoryPayload implements Serializable, ClientPayload {
    public final Integer playerCount;

    public ListMatchHistoryPayload() {
        this(null);
    }

    public ListMatchHistoryPayload(Integer playerCount) {
        this.playerCount = playerCount;
    }
}
