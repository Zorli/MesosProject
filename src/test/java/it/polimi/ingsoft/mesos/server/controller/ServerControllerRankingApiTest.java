package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchRankingEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ServerControllerRankingApiTest {
    @Test
    void preLobbyGlobalRankingIsMappedAndSentToRequester() {
        RecordingRankingService rankingService = new RecordingRankingService();
        ServerController controller = new ServerController(
                DefaultGameFactory.getInstance(),
                rankingService,
                new ServerSessionRegistry()
        );
        RecordingConnection connection = new RecordingConnection();
        String sessionId = controller.registerConnection(connection);
        controller.authenticate(sessionId, "Alice");

        controller.listGlobalRanking(sessionId);

        GlobalRankingPayload payload = assertInstanceOf(GlobalRankingPayload.class, connection.lastUpdate());
        assertEquals("Alice", payload.entries.getFirst().username);
        assertEquals(1, payload.entries.getFirst().globalRank);
    }

    @Test
    void preLobbyMatchHistorySupportsOptionalPlayerCountFilter() {
        RecordingRankingService rankingService = new RecordingRankingService();
        ServerController controller = new ServerController(
                DefaultGameFactory.getInstance(),
                rankingService,
                new ServerSessionRegistry()
        );
        RecordingConnection connection = new RecordingConnection();
        String sessionId = controller.registerConnection(connection);
        controller.authenticate(sessionId, "Alice");

        controller.listMatchHistory(sessionId, null);
        MatchHistoryPayload allHistory = assertInstanceOf(MatchHistoryPayload.class, connection.lastUpdate());
        controller.listMatchHistory(sessionId, 4);
        MatchHistoryPayload filteredHistory = assertInstanceOf(MatchHistoryPayload.class, connection.lastUpdate());

        assertEquals("all-history", allHistory.matches.getFirst().gameId);
        assertEquals("filtered-history", filteredHistory.matches.getFirst().gameId);
        assertEquals(4, rankingService.lastHistoryPlayerCount);
    }

    private static final class RecordingConnection implements PingPongConnection, Listener {
        private final java.util.ArrayList<Object> updates = new java.util.ArrayList<>();

        @Override
        public void sendPing() {
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void notifyUpdate(Object payload) {
            this.updates.add(payload);
        }

        private Object lastUpdate() {
            return this.updates.getLast();
        }
    }

    private static final class RecordingRankingService implements RankingService {
        private int lastHistoryPlayerCount;

        @Override
        public boolean registerUser(String nickname) {
            return true;
        }

        @Override
        public boolean isNicknameAvailable(String nickname) {
            return true;
        }

        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                      int playerCount,
                                                      Instant playedAtUtc,
                                                      String requestingNickname,
                                                      List<MatchScore> finalScores) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LeaderboardEntry> getLeaderboard() {
            return List.of(new LeaderboardEntry("Alice", 1200, 3, 1, 1));
        }

        @Override
        public List<MatchHistory> getMatchHistory() {
            return history("all-history", 2);
        }

        @Override
        public List<MatchHistory> getMatchHistory(int playerCount) {
            this.lastHistoryPlayerCount = playerCount;
            return history("filtered-history", playerCount);
        }

        private static List<MatchHistory> history(String gameId, int playerCount) {
            List<MatchRankingEntry> entries = new java.util.ArrayList<>(List.of(
                    new MatchRankingEntry("Alice", 20, 1, 1000, 12, 1012, 1, 1, 1),
                    new MatchRankingEntry("Bob", 10, 2, 1000, -8, 992, 2, 2, 0),
                    new MatchRankingEntry("Carol", 8, 3, 1000, -10, 990, 3, 3, 0),
                    new MatchRankingEntry("Dave", 5, 4, 1000, -12, 988, 4, 4, 0),
                    new MatchRankingEntry("Eve", 1, 5, 1000, -14, 986, 5, 5, 0)
            ));
            return List.of(new MatchHistory(
                    gameId,
                    Instant.parse("2026-05-06T19:30:00Z"),
                    playerCount,
                    entries.subList(0, playerCount)
            ));
        }
    }
}
