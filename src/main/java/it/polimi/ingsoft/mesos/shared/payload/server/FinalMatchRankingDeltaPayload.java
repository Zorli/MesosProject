package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;

public class FinalMatchRankingDeltaPayload implements Serializable {
    public final String username;
    public final int oldScore;
    public final int newScore;
    public final int previousGlobalRank;
    public final int newGlobalRank;

    public FinalMatchRankingDeltaPayload(String username,
                                         int oldScore,
                                         int newScore,
                                         int previousGlobalRank,
                                         int newGlobalRank) {
        this.username = username;
        this.oldScore = oldScore;
        this.newScore = newScore;
        this.previousGlobalRank = previousGlobalRank;
        this.newGlobalRank = newGlobalRank;
    }
}
