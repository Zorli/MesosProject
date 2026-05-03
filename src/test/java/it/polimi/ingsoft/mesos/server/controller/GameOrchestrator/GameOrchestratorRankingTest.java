package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
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
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GameOrchestratorRankingTest {

    @Test
    void defaultConstructorDerivesHostFromFirstPlayerAndExposesImmutablePlayersList() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players);
        TrackingGameFactory gameFactory = new TrackingGameFactory(game);

        GameOrchestrator orchestrator = new GameOrchestrator(gameFactory, players);

        assertEquals(game, orchestrator.getGame());
        assertEquals(players.size(), orchestrator.getPlayers().size());
        assertEquals(players.getFirst().getNickname(), orchestrator.getPlayers().getFirst().getNickname());
        assertThrows(UnsupportedOperationException.class, () -> orchestrator.getPlayers().add(new Player("Charlie", 0, 0, TotemColor.YELLOW)));
        assertTrue(orchestrator.getFinalRankingResult().isEmpty());
        assertFalse(orchestrator.isFrozen());
    }

    @Test
    void explicitAndDefaultConstructorsValidateInputPreconditions() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGameFactory validFactory = new TrackingGameFactory(new TrackingGame(players));

        assertThrows(NullPointerException.class, () -> new GameOrchestrator(null, players));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(validFactory, null));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(validFactory, new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(validFactory, players, ""));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(validFactory, players, "   "));

        ArrayList<Player> invalidHostPlayers = new ArrayList<>();
        invalidHostPlayers.add(new Player(null, 0, 0, TotemColor.RED));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(validFactory, invalidHostPlayers));
    }

    @Test
    void fourArgConstructorAlsoValidatesPlayersNullOrEmptyAndAllowsValidList() {
        GameFactory gameFactory = new TrackingGameFactory(new TrackingGame(MesosTestFixtures.createPlayers("Alice")));

        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(gameFactory, null, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(gameFactory, new ArrayList<>(), "Alice"));

        ArrayList<Player> validPlayers = MesosTestFixtures.createPlayers("Alice", "Bob");
        GameOrchestrator orchestrator = new GameOrchestrator(gameFactory, validPlayers, "Alice");
        assertEquals(validPlayers, orchestrator.getPlayers());
    }

    @Test
    void hostValidationCoversNullOrBlankNicknameBranchesInConstructors() {
        GameFactory gameFactory = new TrackingGameFactory(new TrackingGame(MesosTestFixtures.createPlayers("Alice")));
        ArrayList<Player> validPlayers = MesosTestFixtures.createPlayers("Alice", "Bob");

        assertThrows(
                IllegalArgumentException.class,
                () -> new GameOrchestrator(gameFactory, validPlayers, (String) null)
        );

        ArrayList<Player> playersWithNullHost = new ArrayList<>();
        playersWithNullHost.add(new Player(null, 0, 0, TotemColor.RED));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameOrchestrator(gameFactory, playersWithNullHost)
        );

        ArrayList<Player> playersWithBlankHost = new ArrayList<>();
        playersWithBlankHost.add(new Player("  ", 0, 0, TotemColor.RED));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameOrchestrator(gameFactory, playersWithBlankHost)
        );

        ArrayList<Player> playersWithNullFirst = new ArrayList<>();
        playersWithNullFirst.add(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new GameOrchestrator(gameFactory, playersWithNullFirst)
        );
    }

    @Test
    void setupMatchForwardsToGameAndRecordsRankingOnlyWhenFinished() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        players.getFirst().gainPrestigePoints(10);
        players.get(1).gainPrestigePoints(4);
        RecordingRankingService rankingService = new RecordingRankingService();

        TrackingGameFactory gameFactory = new TrackingGameFactory(
                new TrackingGame(players, GameStatus.IN_PROGRESS, true, false, false, false)
        );
        GameOrchestrator orchestrator = new GameOrchestrator(
                gameFactory,
                players,
                "Alice",
                rankingService
        );

        orchestrator.setupMatch();

        assertEquals(1, gameFactory.getGame().getSetupCalls());
        assertEquals(1, rankingService.recordCalls);
        assertEquals("7", rankingService.lastGameId);
        assertEquals(2, rankingService.lastPlayerCount);
        assertEquals("Alice", rankingService.requestingNickname);
        assertEquals(
                List.of(new MatchScore("Alice", 10), new MatchScore("Bob", 4)),
                rankingService.lastFinalScores
        );
        assertTrue(orchestrator.getFinalRankingResult().isPresent());
    }

    @Test
    void setupAndGameplayCommandsForwardToGameForKnownPlayerWithoutRankingWhenNotFinished() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players, GameStatus.SETUP, false, false, false, false);
        GameOrchestrator orchestrator = new GameOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                null
        );

        orchestrator.setupMatch();
        orchestrator.moveTotem("Alice", "A1");
        orchestrator.chooseCard("Bob", 7);
        orchestrator.extraDrawn("Alice", 3);

        assertEquals(1, game.getSetupCalls());
        assertEquals(1, game.getMoveTotemCalls());
        assertEquals(1, game.getChooseCardCalls());
        assertEquals(1, game.getExtraDrawnCalls());
        assertEquals(players.getFirst(), game.getLastActionPlayer());
        assertEquals("A1", game.getLastTile());
        assertEquals(3, game.getLastCardId());
        assertEquals(2, game.getRecordedRankingCalls().size());
    }

    @Test
    void unknownPlayerCannotResolveActionAcrossAllMainFlowMethods() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players);
        GameOrchestrator orchestrator = new GameOrchestrator(new TrackingGameFactory(game), players, "Alice");

        assertThrows(IllegalArgumentException.class, () -> orchestrator.moveTotem("Eve", "A1"));
        assertThrows(IllegalArgumentException.class, () -> orchestrator.chooseCard("Eve", 1));
        assertThrows(IllegalArgumentException.class, () -> orchestrator.extraDrawn("Eve", 1));
    }

    @Test
    void rankingShouldNotRunWhenNoServiceProvidedOrGameIsNotFinished() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        GameOrchestrator orchestratorNoService = new GameOrchestrator(
                new TrackingGameFactory(new TrackingGame(players, GameStatus.SETUP, false, true, false, false)),
                players,
                "Alice"
        );
        orchestratorNoService.chooseCard("Alice", 1);
        assertTrue(orchestratorNoService.getFinalRankingResult().isEmpty());

        RecordingRankingService rankingService = new RecordingRankingService();
        GameOrchestrator orchestratorNoFinish = new GameOrchestrator(
                new TrackingGameFactory(new TrackingGame(players, GameStatus.SETUP, false, false, false, false)),
                players,
                "Alice",
                rankingService
        );
        orchestratorNoFinish.setupMatch();

        assertEquals(0, rankingService.recordCalls);
        assertTrue(orchestratorNoFinish.getFinalRankingResult().isEmpty());
    }

    @Test
    void rankingServiceRecordsOnlyOnceEvenWhenMoreCommandsArrive() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGameFactory gameFactory = new TrackingGameFactory(
                new TrackingGame(players, GameStatus.IN_PROGRESS, true, false, false, false)
        );
        RecordingRankingService rankingService = new RecordingRankingService();
        GameOrchestrator orchestrator = new GameOrchestrator(gameFactory, players, "Alice", rankingService);

        orchestrator.setupMatch();
        orchestrator.moveTotem("Bob", "A1");
        orchestrator.extraDrawn("Alice", 2);

        assertEquals(1, rankingService.recordCalls);
        assertEquals(1, gameFactory.getGame().getSetupCalls());
        assertEquals(1, gameFactory.getGame().getMoveTotemCalls());
        assertEquals(0, gameFactory.getGame().getChooseCardCalls());
        assertEquals(1, gameFactory.getGame().getExtraDrawnCalls());
    }

    @Test
    void freezeForDisconnectedPlayerCapturesErrorAndBlocksFutureCommands() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players);
        GameOrchestrator orchestrator = new GameOrchestrator(new TrackingGameFactory(game), players, "Alice");

        orchestrator.freezeForDisconnectedPlayer("Alice");

        ErrorPayload lastPayload = game.getLastNotifiedError();
        assertEquals("PLAYER_DISCONNECTED", lastPayload.code);
        assertTrue(lastPayload.message.contains("Alice"));
        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, () -> orchestrator.chooseCard("Alice", 1));
        assertThrows(MatchFrozenException.class, () -> orchestrator.moveTotem("Alice", "A1"));
        assertThrows(MatchFrozenException.class, () -> orchestrator.extraDrawn("Alice", 2));
        assertThrows(MatchFrozenException.class, () -> orchestrator.setupMatch());
    }

    @Test
    void rankingFinalizationFailureFreezesMatchAndThrowsDomainException() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        GameOrchestrator orchestrator = new GameOrchestrator(
                new TrackingGameFactory(new TrackingGame(players, GameStatus.IN_PROGRESS, false, false, false, true)),
                players,
                "Alice",
                new FailingRankingService()
        );

        assertThrows(
                RankingFinalizationFailureException.class,
                () -> orchestrator.extraDrawn("Alice", 2)
        );
        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, () -> orchestrator.setupMatch());
    }

    private static final class TrackingGameFactory implements GameFactory {
        private final TrackingGame game;

        private TrackingGameFactory(TrackingGame game) {
            this.game = game;
        }

        @Override
        public Game createGame(List<Player> players) {
            return this.game;
        }

        private TrackingGame getGame() {
            return this.game;
        }
    }

    private static final class TrackingGame extends Game {
        private int setupCalls;
        private int moveTotemCalls;
        private int chooseCardCalls;
        private int extraDrawnCalls;
        private Player lastActionPlayer;
        private String lastTile;
        private int lastCardId;
        private ErrorPayload lastNotifiedError;

        private final boolean finishOnSetup;
        private final boolean finishOnMove;
        private final boolean finishOnChoose;
        private final boolean finishOnExtraDrawn;
        private final List<String> recordedRankingCalls = new ArrayList<>();

        private TrackingGame(List<Player> players) {
            this(players, GameStatus.SETUP, false, false, false, false);
        }

        private TrackingGame(List<Player> players,
                             GameStatus initialStatus,
                             boolean finishOnSetup,
                             boolean finishOnMove,
                             boolean finishOnChoose,
                             boolean finishOnExtraDrawn) {
            super(
                    7,
                    players,
                    new TurnState(),
                    new TurnOrderTrack(),
                    new Board(
                            TribeDeck.fromOrderedCards(new ArrayList<>()),
                            BuildingDeck.fromOrderedCards(new ArrayList<>()),
                            players
                    )
            );
            this.setStatus(initialStatus);
            this.finishOnSetup = finishOnSetup;
            this.finishOnMove = finishOnMove;
            this.finishOnChoose = finishOnChoose;
            this.finishOnExtraDrawn = finishOnExtraDrawn;
        }

        @Override
        public void setup() {
            this.setupCalls++;
            if (this.finishOnSetup) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void moveTotem(Player player, String tile) {
            this.moveTotemCalls++;
            this.lastActionPlayer = player;
            this.lastTile = tile;
            if (this.finishOnMove) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void chooseCard(Player player, int cardId) {
            this.chooseCardCalls++;
            this.lastActionPlayer = player;
            this.lastCardId = cardId;
            this.recordedRankingCalls.add("chooseCard");
            if (this.finishOnChoose) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void extraDrawn(Player player, int cardId) {
            this.extraDrawnCalls++;
            this.lastActionPlayer = player;
            this.lastCardId = cardId;
            this.recordedRankingCalls.add("extraDrawn");
            if (this.finishOnExtraDrawn) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void notifyError(ErrorPayload payload) {
            this.lastNotifiedError = payload;
        }

        private int getSetupCalls() {
            return this.setupCalls;
        }

        private int getMoveTotemCalls() {
            return this.moveTotemCalls;
        }

        private int getChooseCardCalls() {
            return this.chooseCardCalls;
        }

        private int getExtraDrawnCalls() {
            return this.extraDrawnCalls;
        }

        private Player getLastActionPlayer() {
            return this.lastActionPlayer;
        }

        private String getLastTile() {
            return this.lastTile;
        }

        private int getLastCardId() {
            return this.lastCardId;
        }

        private List<String> getRecordedRankingCalls() {
            return this.recordedRankingCalls;
        }

        private ErrorPayload getLastNotifiedError() {
            return this.lastNotifiedError;
        }
    }

    private static class RecordingRankingServiceAdapter implements RankingService {
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
            throw new UnsupportedOperationException("Override in concrete service");
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

    private static final class RecordingRankingService extends RecordingRankingServiceAdapter {
        private int recordCalls;
        private String lastGameId;
        private int lastPlayerCount;
        private String requestingNickname;
        private List<MatchScore> lastFinalScores = List.of();

        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                     int playerCount,
                                                     Instant playedAtUtc,
                                                     String requestingNickname,
                                                     List<MatchScore> finalScores) {
            this.recordCalls++;
            this.lastGameId = gameId;
            this.lastPlayerCount = playerCount;
            this.requestingNickname = requestingNickname;
            this.lastFinalScores = List.copyOf(finalScores);
            return new RankingResult(
                    1,
                    List.of(new LeaderboardEntry(requestingNickname, 1200, 0, 0, 1))
            );
        }
    }

    private static final class FailingRankingService extends RecordingRankingServiceAdapter {
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
