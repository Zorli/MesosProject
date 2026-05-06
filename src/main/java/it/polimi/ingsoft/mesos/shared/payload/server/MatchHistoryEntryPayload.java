package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;
import java.util.List;

public class MatchHistoryEntryPayload implements Serializable {
    public final String gameId;
    public final String playedAtUtc;
    public final int playerCount;
    public final List<MatchHistoryStandingPayload> standings;

    public MatchHistoryEntryPayload(String gameId,
                                    String playedAtUtc,
                                    int playerCount,
                                    List<MatchHistoryStandingPayload> standings) {
        this.gameId = gameId;
        this.playedAtUtc = playedAtUtc;
        this.playerCount = playerCount;
        this.standings = standings == null ? List.of() : List.copyOf(standings);
    }
}
