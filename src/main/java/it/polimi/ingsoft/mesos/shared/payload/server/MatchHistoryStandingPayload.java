package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;

public class MatchHistoryStandingPayload implements Serializable {
    public final String username;
    public final int finalScore;
    public final int placement;
    public final int oldScore;
    public final int scoreDelta;
    public final int newScore;
    public final int previousGlobalRank;
    public final int newGlobalRank;
    public final int winStreakAfter;

    public MatchHistoryStandingPayload(String username,
                                       int finalScore,
                                       int placement,
                                       int oldScore,
                                       int scoreDelta,
                                       int newScore,
                                       int previousGlobalRank,
                                       int newGlobalRank,
                                       int winStreakAfter) {
        this.username = username;
        this.finalScore = finalScore;
        this.placement = placement;
        this.oldScore = oldScore;
        this.scoreDelta = scoreDelta;
        this.newScore = newScore;
        this.previousGlobalRank = previousGlobalRank;
        this.newGlobalRank = newGlobalRank;
        this.winStreakAfter = winStreakAfter;
    }
}
