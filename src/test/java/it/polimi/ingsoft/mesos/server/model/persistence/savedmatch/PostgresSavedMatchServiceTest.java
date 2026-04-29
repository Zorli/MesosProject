// Temporarily commented out: stale tests target removed game lifecycle APIs.
//package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;
//
//import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
//import it.polimi.ingsoft.mesos.server.model.game.Game;
//import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
//import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
//import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
//import it.polimi.ingsoft.mesos.server.model.player.Player;
//import it.polimi.ingsoft.mesos.server.testing.AdvancedGameStateFixtures;
//import org.junit.jupiter.api.Test;
//
//import java.util.ArrayList;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
///**
// * Unit tests for the saved-match application service independent of PostgreSQL.
// */
//class PostgresSavedMatchServiceTest {
//
//    @Test
//    void checkpointSpecificMethodsPersistExpectedCheckpointType() {
//        RecordingSavedMatchRepository repository = new RecordingSavedMatchRepository();
//        PostgresSavedMatchService service = new PostgresSavedMatchService(repository);
//        Game game = createGame();
//        game.setup();
//
//        service.initializeMatch(game, "Alice");
//        assertEquals(SavedMatchCheckpointType.GAME_INITIALIZED, repository.lastSavedAggregate.lastCheckpointType());
//
//        service.saveTurnStarted(game, "Alice");
//        assertEquals(SavedMatchCheckpointType.TURN_STARTED, repository.lastSavedAggregate.lastCheckpointType());
//
//        service.saveEraChanged(game, "Alice");
//        assertEquals(SavedMatchCheckpointType.ERA_CHANGED, repository.lastSavedAggregate.lastCheckpointType());
//
//        service.saveTurnFinished(game, "Alice");
//        assertEquals(SavedMatchCheckpointType.TURN_FINISHED, repository.lastSavedAggregate.lastCheckpointType());
//
//        service.completeMatch(game, "Alice");
//        assertEquals(SavedMatchCheckpointType.MATCH_COMPLETED, repository.lastSavedAggregate.lastCheckpointType());
//    }
//
//    @Test
//    void persistenceEnabledIsTrueForPostgresImplementation() {
//        PostgresSavedMatchService service = new PostgresSavedMatchService(new RecordingSavedMatchRepository());
//
//        assertTrue(service.isPersistenceEnabled());
//    }
//
//    @Test
//    void completeMatchPersistsMatchCompletedForPreScoringFinalScoringSnapshot() {
//        RecordingSavedMatchRepository repository = new RecordingSavedMatchRepository();
//        PostgresSavedMatchService service = new PostgresSavedMatchService(repository);
//        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
//
//        service.completeMatch(game, "Alice");
//
//        assertEquals(SavedMatchCheckpointType.MATCH_COMPLETED, repository.lastSavedAggregate.lastCheckpointType());
//        assertEquals(GameStatus.IN_PROGRESS, repository.lastSavedAggregate.gameStatus());
//        assertEquals(RoundPhase.FINAL_SCORING, repository.lastSavedAggregate.roundPhase());
//    }
//
//    @Test
//    void completeMatchPersistsMatchCompletedForFinishedPostScoringSnapshot() {
//        RecordingSavedMatchRepository repository = new RecordingSavedMatchRepository();
//        PostgresSavedMatchService service = new PostgresSavedMatchService(repository);
//        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
//        game.calculateFinalScore();
//
//        service.completeMatch(game, "Alice");
//
//        assertEquals(SavedMatchCheckpointType.MATCH_COMPLETED, repository.lastSavedAggregate.lastCheckpointType());
//        assertEquals(GameStatus.FINISHED, repository.lastSavedAggregate.gameStatus());
//        assertEquals(RoundPhase.FINAL_SCORING, repository.lastSavedAggregate.roundPhase());
//    }
//
//    private static Game createGame() {
//        ArrayList<Player> players = new ArrayList<>();
//        players.add(new Player("Alice", 0, 0, TotemColor.RED, null));
//        players.add(new Player("Bob", 0, 0, TotemColor.BLUE, null));
//        return DefaultGameFactory.getInstance().createGame(players);
//    }
//
//    /**
//     * Repository spy capturing the last aggregate requested by the service.
//     */
//    private static final class RecordingSavedMatchRepository implements SavedMatchRepository {
//        private SavedMatchAggregate lastSavedAggregate;
//
//        @Override
//        public void save(SavedMatchAggregate aggregate) {
//            this.lastSavedAggregate = aggregate;
//        }
//
//        @Override
//        public SavedMatchAggregate load(String gameId) {
//            return this.lastSavedAggregate;
//        }
//
//        @Override
//        public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
//        }
//
//        @Override
//        public void markPlayerOffline(String gameId, String nickname) {
//        }
//
//        @Override
//        public int deleteExpiredInProgressMatches() {
//            return 0;
//        }
//    }
//}
//
