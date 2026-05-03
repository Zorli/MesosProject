package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameOrchestratorRankingTest {

    @Test
    void recordsRankingOnceWhenGameReachesFinishedState() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        players.getFirst().gainPrestigePoints(10);
        players.get(1).gainPrestigePoints(4);
        RecordingRankingService rankingService = new RecordingRankingService();
        GameOrchestrator orchestrator = new GameOrchestrator(
                new FinishingGameFactory(42),
                players,
                "Alice",
                rankingService
        );

        orchestrator.chooseCard("Alice", 1);
        orchestrator.chooseCard("Alice", 1);

        assertEquals(1, rankingService.recordCalls);
        assertEquals("42", rankingService.gameId);
        assertEquals(2, rankingService.playerCount);
        assertEquals("Alice", rankingService.requestingNickname);
        assertEquals(List.of(new MatchScore("Alice", 10), new MatchScore("Bob", 4)), rankingService.finalScores);
        assertTrue(orchestrator.getFinalRankingResult().isPresent());
    }

    @Test
    void freezesMatchWhenFinalRankingUpdateFails() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        GameOrchestrator orchestrator = new GameOrchestrator(
                new FinishingGameFactory(7),
                players,
                "Alice",
                new FailingRankingService()
        );

        assertThrows(RankingFinalizationFailureException.class, () -> orchestrator.chooseCard("Alice", 1));

        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, () -> orchestrator.chooseCard("Alice", 1));
    }

    private static final class FinishingGameFactory implements GameFactory {
        private final int gameId;

        private FinishingGameFactory(int gameId) {
            this.gameId = gameId;
        }

        @Override
        public Game createGame(List<Player> players) {
            return new FinishingGame(this.gameId, players);
        }
    }

    private static final class FinishingGame extends Game {
        private FinishingGame(int id, List<Player> players) {
            super(
                    id,
                    players,
                    new TurnState(),
                    new TurnOrderTrack(),
                    new Board(
                            TribeDeck.fromOrderedCards(new ArrayList<>()),
                            BuildingDeck.fromOrderedCards(new ArrayList<>()),
                            players
                    )
            );
        }

        @Override
        public void chooseCard(Player player, int cardId) {
            this.setStatus(GameStatus.FINISHED);
        }
    }

    private static class RankingServiceAdapter implements RankingService {
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
            return new RankingResult(1, List.of(new LeaderboardEntry(requestingNickname, 1000, 0, 0, 1)));
        }

        @Override
        public List<LeaderboardEntry> getLeaderboard() {
            return List.of();
        }

        @Override
        public List<MatchHistory> getMatchHistory() {
            return List.of();
        }

        @Override
        public List<MatchHistory> getMatchHistory(int playerCount) {
            return List.of();
        }
    }

    private static final class RecordingRankingService extends RankingServiceAdapter {
        private int recordCalls;
        private String gameId;
        private int playerCount;
        private String requestingNickname;
        private List<MatchScore> finalScores = List.of();

        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                      int playerCount,
                                                      Instant playedAtUtc,
                                                      String requestingNickname,
                                                      List<MatchScore> finalScores) {
            this.recordCalls++;
            this.gameId = gameId;
            this.playerCount = playerCount;
            this.requestingNickname = requestingNickname;
            this.finalScores = List.copyOf(finalScores);
            return super.recordMatchAndGetRanking(gameId, playerCount, playedAtUtc, requestingNickname, finalScores);
        }
    }

    private static final class FailingRankingService extends RankingServiceAdapter {
        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                      int playerCount,
                                                      Instant playedAtUtc,
                                                      String requestingNickname,
                                                      List<MatchScore> finalScores) {
            throw new IllegalStateException("ranking backend unavailable");
        }
    }
}
