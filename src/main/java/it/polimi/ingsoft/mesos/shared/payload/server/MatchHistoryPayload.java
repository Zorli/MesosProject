package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;
import java.util.List;

public class MatchHistoryPayload implements Serializable {
    public final List<MatchHistoryEntryPayload> matches;

    public MatchHistoryPayload(List<MatchHistoryEntryPayload> matches) {
        this.matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
