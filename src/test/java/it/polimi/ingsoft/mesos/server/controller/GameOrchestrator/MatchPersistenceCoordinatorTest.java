package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchPersistenceCoordinatorTest {

    @Test
    void recordAfterMoveTotemPersistsTurnStartedOnlyWhenPlacementTransitionsToDraws() {
        RecordingSavedMatchService service = new RecordingSavedMatchService();
        MatchPersistenceCoordinator coordinator = new MatchPersistenceCoordinator(service, "Alice");
        Game game = createSetupGame();

        game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "B");
        coordinator.recordAfterMoveTotem(game, RoundPhase.PLACING_TOTEMS);
        assertEquals(0, service.turnStartedCalls);

        game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "C");
        coordinator.recordAfterMoveTotem(game, RoundPhase.PLACING_TOTEMS);
        assertEquals(1, service.turnStartedCalls);
        assertSame(game, service.lastGame);
        assertEquals("Alice", service.lastHostNickname);
    }

    @Test
    void explicitCheckpointMethodsDelegateToSavedMatchService() {
        RecordingSavedMatchService service = new RecordingSavedMatchService();
        MatchPersistenceCoordinator coordinator = new MatchPersistenceCoordinator(service, "Alice");
        Game game = createSetupGame();

        coordinator.recordTurnStarted(game);
        coordinator.recordMatchInitialized(game);
        coordinator.recordTurnFinished(game);
        coordinator.recordEraChanged(game);
        coordinator.recordMatchCompleted(game);

        assertEquals(1, service.turnStartedCalls);
        assertEquals(1, service.initializeCalls);
        assertEquals(1, service.turnFinishedCalls);
        assertEquals(1, service.eraChangedCalls);
        assertEquals(1, service.completedCalls);
        assertTrue(coordinator.isPersistenceEnabled());
    }

    @Test
    void constructorRejectsNullServiceAndBlankHostNickname() {
        RecordingSavedMatchService service = new RecordingSavedMatchService();

        assertThrows(NullPointerException.class, () -> new MatchPersistenceCoordinator(null, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> new MatchPersistenceCoordinator(service, " "));
    }

    @Test
    void recordAfterMoveTotemRejectsNullArgumentsAndSkipsNonDrawTransitions() {
        RecordingSavedMatchService service = new RecordingSavedMatchService();
        MatchPersistenceCoordinator coordinator = new MatchPersistenceCoordinator(service, "Alice");
        Game game = createSetupGame();

        assertThrows(NullPointerException.class, () -> coordinator.recordAfterMoveTotem(null, RoundPhase.PLACING_TOTEMS));
        assertThrows(NullPointerException.class, () -> coordinator.recordAfterMoveTotem(game, null));

        coordinator.recordAfterMoveTotem(game, RoundPhase.RESOLVING_EFFECTS);

        assertEquals(0, service.turnStartedCalls);
    }

    private static Game createSetupGame() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers(2);
        Game game = DefaultGameFactory.getInstance().createGame(players);
        game.setup();
        return game;
    }

    private static final class RecordingSavedMatchService implements SavedMatchService {
        private int initializeCalls;
        private int turnStartedCalls;
        private int turnFinishedCalls;
        private int eraChangedCalls;
        private int completedCalls;
        private Game lastGame;
        private String lastHostNickname;

        @Override
        public boolean isPersistenceEnabled() {
            return true;
        }

        @Override
        public void initializeMatch(Game game, String hostNickname) {
            this.initializeCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
            this.turnStartedCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
            this.eraChangedCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
            this.turnFinishedCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
            this.completedCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
        }

        @Override
        public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
        }

        @Override
        public SavedMatchRestoreResult restoreMatch(String gameId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
        }

        @Override
        public void markPlayerOffline(String gameId, String nickname) {
        }

        @Override
        public int deleteExpiredInProgressMatches() {
            return 0;
        }
    }
}
