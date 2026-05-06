package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;

public class GlobalRankingEntryPayload implements Serializable {
    public final String username;
    public final int score;
    public final int globalRank;
    public final int wins;
    public final int winStreak;

    public GlobalRankingEntryPayload(String username, int score, int globalRank, int wins, int winStreak) {
        this.username = username;
        this.score = score;
        this.globalRank = globalRank;
        this.wins = wins;
        this.winStreak = winStreak;
    }
}
