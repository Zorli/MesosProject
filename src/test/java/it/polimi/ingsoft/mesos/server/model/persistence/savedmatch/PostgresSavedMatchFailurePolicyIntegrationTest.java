package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class PostgresSavedMatchFailurePolicyIntegrationTest extends PostgresIntegrationTestSupport {

    //@Test
//    void setupCheckpointFailureFreezesMatchAndLeavesNoDurableSnapshot() {
//        PostgresSavedMatchService persistentService = this.createService("saved_match_failure_setup");
//        FailingCheckpointService service = new FailingCheckpointService(persistentService);
//        service.failInitializeMatch = true;
//
//        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
//        LobbyManager manager = new LobbyManager();
//        Lobby lobby = manager.createLobby(players.getFirst());
//        manager.joinLobby(lobby.getId(), players.get(1));
//
//        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
//
//        MatchPersistenceFailureException exception = assertThrows(
//                MatchPersistenceFailureException.class,
//                orchestrator::setupMatch
//        );
//
//        assertEquals("Failed to persist checkpoint GAME_INITIALIZED; match is now frozen", exception.getMessage());
//        assertTrue(orchestrator.isFrozen());
//        assertThrows(MatchFrozenException.class, orchestrator::setupMatch);
//        assertThrows(
//                IllegalArgumentException.class,
//                () -> persistentService.restoreGame(String.valueOf(orchestrator.getGame().getId()))
//        );
//    }

//    @Test
//    void turnStartedCheckpointFailureFreezesMatchAndKeepsLastDurableSnapshot() {
//        PostgresSavedMatchService persistentService = this.createService("saved_match_failure_turn_started");
//        FailingCheckpointService service = new FailingCheckpointService(persistentService);
//        service.failTurnStarted = true;
//
//        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
//        LobbyManager manager = new LobbyManager();
//        Lobby lobby = manager.createLobby(players.getFirst());
//        manager.joinLobby(lobby.getId(), players.get(1));
//
//        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
//        orchestrator.setupMatch();
//
//        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");
//
//        MatchPersistenceFailureException exception = assertThrows(
//                MatchPersistenceFailureException.class,
//                () -> orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C")
//        );
//
//        assertEquals("Failed to persist checkpoint TURN_STARTED; match is now frozen", exception.getMessage());
//        assertTrue(orchestrator.isFrozen());
//        assertEquals(RoundPhase.PLAYER_DRAWS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
//        assertThrows(
//                MatchFrozenException.class,
//                () -> orchestrator.moveTotem(orchestrator.getGame().getPlayers().getFirst(), "D")
//        );
//
//        Game restoredGame = persistentService.restoreGame(String.valueOf(orchestrator.getGame().getId()));
//
//        assertEquals(RoundPhase.PLACING_TOTEMS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
//        assertEquals(1, restoredGame.getTurnOrderTrack().getRoundNumber());
//        assertEquals(0, restoredGame.getTurnOrderTrack().getCurrentPlayerIndex());
//    }

//    private PostgresSavedMatchService createService(String dbPrefix) {
//        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig(dbPrefix, true);
//        PostgresDatabaseBootstrap.getInstance().ensureReady(
//                runtimeConfig,
//                List.of(PostgresSavedMatchSchemaModule.getInstance())
//        );
//
//        return new PostgresSavedMatchService(
//                runtimeConfig.toPostgresConnectionConfig(),
//                runtimeConfig.maxBusyRetries()
//        );
//    }

    private static final class FailingCheckpointService implements SavedMatchService {
        private final SavedMatchService delegate;
        private boolean failInitializeMatch;
        private boolean failTurnStarted;

        private FailingCheckpointService(SavedMatchService delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isPersistenceEnabled() {
            return this.delegate.isPersistenceEnabled();
        }

        @Override
        public void initializeMatch(Game game, String hostNickname) {
            if (this.failInitializeMatch) {
                throw new IllegalStateException("Injected initialize failure");
            }
            this.delegate.initializeMatch(game, hostNickname);
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
            if (this.failTurnStarted) {
                throw new IllegalStateException("Injected turn-started failure");
            }
            this.delegate.saveTurnStarted(game, hostNickname);
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
            this.delegate.saveEraChanged(game, hostNickname);
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
            this.delegate.saveTurnFinished(game, hostNickname);
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
            this.delegate.completeMatch(game, hostNickname);
        }

        @Override
        public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
            this.delegate.saveMatch(game, hostNickname, checkpointType);
        }

        @Override
        public SavedMatchRestoreResult restoreMatch(String gameId) {
            return this.delegate.restoreMatch(gameId);
        }

        @Override
        public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
            this.delegate.markPlayerOnline(gameId, nickname, sessionToken);
        }

        @Override
        public void markPlayerOffline(String gameId, String nickname) {
            this.delegate.markPlayerOffline(gameId, nickname);
        }

        @Override
        public int deleteExpiredInProgressMatches() {
            return this.delegate.deleteExpiredInProgressMatches();
        }
    }
}
