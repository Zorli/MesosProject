package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;
import java.util.List;

public class GlobalRankingPayload implements Serializable {
    public final List<GlobalRankingEntryPayload> entries;

    public GlobalRankingPayload(List<GlobalRankingEntryPayload> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
