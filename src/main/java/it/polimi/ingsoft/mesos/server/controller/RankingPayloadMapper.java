package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchRankingEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.PlayerRankingDelta;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingDeltaPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingStatus;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingEntryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryEntryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryStandingPayload;

import java.util.List;

public final class RankingPayloadMapper {
    public GlobalRankingPayload globalRanking(List<LeaderboardEntry> entries) {
        return new GlobalRankingPayload(entries.stream()
                .map(entry -> new GlobalRankingEntryPayload(
                        entry.nickname(),
                        entry.elo(),
                        entry.rank(),
                        entry.wins(),
                        entry.winStreak()
                ))
                .toList());
    }

    public FinalMatchRankingPayload finalMatchRanking(String gameId, RankingResult result) {
        FinalMatchRankingStatus status = result.playerDeltas().isEmpty()
                ? FinalMatchRankingStatus.ALREADY_RECORDED
                : FinalMatchRankingStatus.RECORDED;
        return new FinalMatchRankingPayload(
                gameId,
                status,
                result.playerDeltas().stream()
                        .map(this::finalMatchRankingDelta)
                        .toList(),
                null
        );
    }

    public FinalMatchRankingPayload rankingDisabled(String gameId) {
        return new FinalMatchRankingPayload(
                gameId,
                FinalMatchRankingStatus.DISABLED,
                List.of(),
                "Ranking persistence is disabled"
        );
    }

    public FinalMatchRankingPayload rankingFailed(String gameId, String message) {
        return new FinalMatchRankingPayload(
                gameId,
                FinalMatchRankingStatus.FAILED,
                List.of(),
                message
        );
    }

    public MatchHistoryPayload history(List<MatchHistory> matches) {
        return new MatchHistoryPayload(matches.stream()
                .map(this::historyEntry)
                .toList());
    }

    private FinalMatchRankingDeltaPayload finalMatchRankingDelta(PlayerRankingDelta delta) {
        return new FinalMatchRankingDeltaPayload(
                delta.nickname(),
                delta.oldScore(),
                delta.newScore(),
                delta.previousGlobalRank(),
                delta.newGlobalRank()
        );
    }

    private MatchHistoryEntryPayload historyEntry(MatchHistory history) {
        return new MatchHistoryEntryPayload(
                history.gameId(),
                history.playedAtUtc().toString(),
                history.playerCount(),
                history.ranking().stream()
                        .map(this::historyStanding)
                        .toList()
        );
    }

    private MatchHistoryStandingPayload historyStanding(MatchRankingEntry entry) {
        return new MatchHistoryStandingPayload(
                entry.nickname(),
                entry.finalScore(),
                entry.placement(),
                entry.eloBefore(),
                entry.eloDelta(),
                entry.eloAfter(),
                entry.previousGlobalRank(),
                entry.newGlobalRank(),
                entry.winStreakAfter()
        );
    }
}
