package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import it.polimi.ingsoft.mesos.server.controller.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.lobby.LobbyTestPlayers.playerWithNickname;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests lobby-manager lifecycle behavior for fresh and resumable lobbies.
 */
class LobbyManagerTest {

    @Test
    void constructorThrowsWhenGameFactoryIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new LobbyManager(null));
    }

    @Test
    void createLobbyStoresLobbyAndAssignsIncrementalIds() {
        LobbyManager manager = new LobbyManager();

        Lobby firstLobby = manager.createLobby(playerWithNickname("Alice"));
        Lobby secondLobby = manager.createLobby(playerWithNickname("Bob"));

        assertEquals(1, firstLobby.getId());
        assertEquals(2, secondLobby.getId());
        assertTrue(manager.getLobby(1).isPresent());
        assertTrue(manager.getLobby(2).isPresent());
    }

    @Test
    void joinLobbySetsStatusToReadyWhenLobbyCanStart() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));

        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
        assertTrue(manager.canStart(lobby.getId()));
    }

    @Test
    void removeHostDestroysLobbyWhenHostWasAlone() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Lobby lobby = manager.createLobby(host);

        manager.removePlayer(lobby.getId(), host);

        assertEquals(LobbyStatus.CLOSED, lobby.getStatus());
        assertTrue(manager.getLobby(lobby.getId()).isEmpty());
    }

    @Test
    void removeHostTransfersHostToSecondPlayerWhenLobbyIsNotEmpty() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);

        manager.removePlayer(lobby.getId(), host);

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals("Bob", updatedLobby.getHostNickname());
    }

    @Test
    void removePlayerSetsStatusBackToWaitingWhenBelowMinimum() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);

        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());

        manager.removePlayer(lobby.getId(), secondPlayer);

        assertEquals(LobbyStatus.WAITING, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void joinLobbyThrowsWhenLobbyIsStarted() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        lobby.setStatus(LobbyStatus.STARTED);

        assertThrows(IllegalStateException.class,
                () -> manager.joinLobby(lobby.getId(), playerWithNickname("Bob")));
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyIsAlreadyStarted() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));
        lobby.setStatus(LobbyStatus.STARTED);

        assertThrows(IllegalStateException.class, () -> manager.createGameFromLobby(lobby.getId()));
    }

    @Test
    void createGameFromLobbyReturnsGameOrchestratorAndStartsLobby() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        GameOrchestrator gameOrchestrator = manager.createGameFromLobby(lobby.getId());

        assertInstanceOf(GameOrchestrator.class, gameOrchestrator);
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyDoesNotExist() {
        LobbyManager manager = new LobbyManager();

        assertThrows(IllegalArgumentException.class, () -> manager.createGameFromLobby(999));
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyIsWaiting() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));

        assertThrows(IllegalStateException.class, () -> manager.createGameFromLobby(lobby.getId()));
    }

    @Test
    void createGameFromLobbyThrowsWhenLobbyIsClosed() {
        LobbyManager manager = new LobbyManager();
        Player host = playerWithNickname("Alice");
        Lobby lobby = manager.createLobby(host);
        manager.removePlayer(lobby.getId(), host);

        assertThrows(IllegalArgumentException.class, () -> manager.createGameFromLobby(lobby.getId()));
    }

    @Test
    void createGameFromLobbyUsesInjectedFactoryAndPassesLobbyPlayersInOrder() {
        RecordingGameFactory gameFactory = new RecordingGameFactory();
        LobbyManager manager = new LobbyManager(gameFactory);
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);
        List<Player> expectedPlayers = lobby.getPlayers();

        GameOrchestrator gameOrchestrator = manager.createGameFromLobby(lobby.getId());

        assertInstanceOf(GameOrchestrator.class, gameOrchestrator);
        assertEquals(1, gameFactory.createGameCalls);
        assertEquals(expectedPlayers, gameFactory.capturedPlayers);
        assertNotSame(expectedPlayers, gameFactory.capturedPlayers);
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void createGameFromLobbyKeepsLobbyReadyWhenFactoryFails() {
        RecordingGameFactory gameFactory = new RecordingGameFactory();
        gameFactory.failure = new IllegalStateException("Factory failure");
        LobbyManager manager = new LobbyManager(gameFactory);
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.createGameFromLobby(lobby.getId())
        );

        assertEquals("Factory failure", exception.getMessage());
        assertEquals(1, gameFactory.createGameCalls);
        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void listOpenLobbiesExcludesStartedAndClosedLobbies() {
        LobbyManager manager = new LobbyManager();

        Lobby waitingLobby = manager.createLobby(playerWithNickname("Alice"));
        Lobby startedLobby = manager.createLobby(playerWithNickname("Bob"));
        startedLobby.setStatus(LobbyStatus.STARTED);

        Lobby closedLobby = manager.createLobby(playerWithNickname("Carol"));
        closedLobby.setStatus(LobbyStatus.CLOSED);

        List<Lobby> openLobbies = manager.listOpenLobbies();

        assertEquals(1, openLobbies.size());
        assertEquals(waitingLobby.getId(), openLobbies.get(0).getId());
    }

    @Test
    void createResumeLobbyStoresResumableLobby() {
        LobbyManager manager = new LobbyManager();

        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));

        assertTrue(lobby.isResumable());
        assertEquals(LobbyStatus.RESUMABLE, lobby.getStatus());
        assertEquals("game-77", lobby.getResumableGameId());
        assertTrue(manager.getLobby(lobby.getId()).isPresent());
    }

    @Test
    void createResumeLobbyAdvancesLobbyIdsLikeStandardCreation() {
        LobbyManager manager = new LobbyManager();

        Lobby first = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        Lobby second = manager.createLobby(playerWithNickname("Carol"));

        assertEquals(1, first.getId());
        assertEquals(2, second.getId());
    }

    @Test
    void joinLobbySetsResumableLobbyToReadyWhenAllOriginalPlayersReconnect() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));

        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        assertEquals(LobbyStatus.RESUMABLE, manager.getLobby(lobby.getId()).orElseThrow().getStatus());

        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));
        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void removingLastPlayerFromResumableLobbyKeepsLobbyOpen() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));

        manager.removePlayer(lobby.getId(), playerWithNickname("Alice"));

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals(LobbyStatus.RESUMABLE, updatedLobby.getStatus());
        assertTrue(updatedLobby.getPlayers().isEmpty());
    }

    @Test
    void removingPlayerFromReadyResumableLobbyReturnsItToResumableStatus() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());

        manager.removePlayer(lobby.getId(), playerWithNickname("Bob"));

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals(LobbyStatus.RESUMABLE, updatedLobby.getStatus());
        assertFalse(manager.canStart(lobby.getId()));
    }

    @Test
    void createGameFromLobbyRejectsResumableLobbyBecauseRestoreFlowIsSeparate() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.createGameFromLobby(lobby.getId())
        );

        assertEquals("Resumable lobbies must restore a saved match instead of creating a new one", exception.getMessage());
    }

    @Test
    void resumeGameFromLobbyRestoresReadyResumableLobbyAndStartsIt() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));
        RecordingRestoreService restoreService = new RecordingRestoreService(restoredGame("Alice", "Bob"), "Alice");

        GameOrchestrator gameOrchestrator = manager.resumeGameFromLobby(lobby.getId(), restoreService);

        assertEquals("game-77", restoreService.capturedGameId);
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
        assertEquals(List.of("Alice", "Bob"), gameOrchestrator.getPlayers().stream().map(Player::getNickname).toList());
    }

    @Test
    void resumeGameFromLobbyRejectsRestoredRosterMismatch() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.resumeGameFromLobby(
                        lobby.getId(),
                        new RecordingRestoreService(restoredGame("Alice", "Carol"), "Alice")
                )
        );

        assertEquals("Restored match roster does not match resumable lobby roster", exception.getMessage());
        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void resumeGameFromLobbyKeepsPersistedHostAfterLobbyHostTransfer() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));
        manager.removePlayer(lobby.getId(), playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        RecordingRestoreService restoreService = new RecordingRestoreService(restoredPlacementGame("Alice", "Bob"), "Alice");

        GameOrchestrator orchestrator = manager.resumeGameFromLobby(lobby.getId(), restoreService);

        assertEquals("Bob", manager.getLobby(lobby.getId()).orElseThrow().getHostNickname());

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");
        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C");

        assertEquals("Alice", restoreService.lastSavedHostNickname);
    }

    @Test
    void resumeGameFromLobbyKeepsPersistedHostAfterPartialReconnectChangesLobbyHost() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Alice"));
        RecordingRestoreService restoreService = new RecordingRestoreService(restoredPlacementGame("Alice", "Bob"), "Alice");

        GameOrchestrator orchestrator = manager.resumeGameFromLobby(lobby.getId(), restoreService);

        assertEquals("Bob", manager.getLobby(lobby.getId()).orElseThrow().getHostNickname());

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");
        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C");

        assertEquals("Alice", restoreService.lastSavedHostNickname);
    }

    @Test
    void getLobbyReturnsEmptyWhenLobbyDoesNotExist() {
        LobbyManager manager = new LobbyManager();

        assertFalse(manager.getLobby(999).isPresent());
    }

    @Test
    void joinLobbyRejectsNullPlayerAndUnknownLobby() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));

        assertThrows(IllegalArgumentException.class, () -> manager.joinLobby(lobby.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> manager.joinLobby(999, playerWithNickname("Bob")));
    }

    @Test
    void exitLobbyDelegatesToRemovePlayer() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        Player bob = playerWithNickname("Bob");
        manager.joinLobby(lobby.getId(), bob);

        manager.exitLobby(lobby.getId(), bob);

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals(1, updatedLobby.getPlayers().size());
        assertEquals("Alice", updatedLobby.getPlayers().get(0).getNickname());
        assertEquals(LobbyStatus.WAITING, updatedLobby.getStatus());
    }

    @Test
    void createGameFromLobbyRejectsNullSavedMatchService() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), playerWithNickname("Bob"));

        assertThrows(IllegalArgumentException.class, () -> manager.createGameFromLobby(lobby.getId(), null));
    }

    @Test
    void resumeGameFromLobbyRejectsNullServiceNonResumableAndNotReadyLobby() {
        LobbyManager manager = new LobbyManager();
        Lobby standardLobby = manager.createLobby(playerWithNickname("Alice"));
        Lobby resumableLobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));

        assertThrows(IllegalArgumentException.class, () -> manager.resumeGameFromLobby(resumableLobby.getId(), null));
        assertThrows(IllegalStateException.class, () -> manager.resumeGameFromLobby(standardLobby.getId(), new RecordingRestoreService(restoredGame("Alice", "Bob"), "Alice")));
        assertThrows(IllegalStateException.class, () -> manager.resumeGameFromLobby(resumableLobby.getId(), new RecordingRestoreService(restoredGame("Alice", "Bob"), "Alice")));
    }

    @Test
    void removePlayerRejectsNullAndUnknownLobby() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(playerWithNickname("Alice"));

        assertThrows(IllegalArgumentException.class, () -> manager.removePlayer(lobby.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> manager.removePlayer(999, playerWithNickname("Alice")));
    }

    @Test
    void toStringIncludesTrackedLobbyCount() {
        LobbyManager manager = new LobbyManager();
        manager.createLobby(playerWithNickname("Alice"));
        manager.createLobby(playerWithNickname("Bob"));

        String description = manager.toString();

        assertTrue(description.contains("nextLobbyId=3"));
        assertTrue(description.contains("lobbiesCount=2"));
    }

    private static final class RecordingGameFactory implements GameFactory {
        private int createGameCalls;
        private ArrayList<Player> capturedPlayers;
        private RuntimeException failure;

        @Override
        public Game createGame(List<Player> players) {
            this.createGameCalls++;
            this.capturedPlayers = new ArrayList<>(players);

            if (this.failure != null) {
                throw this.failure;
            }

            return null;
        }
    }

    /**
     * Saved-match service stub returning a predetermined restored game.
     */
    private static final class RecordingRestoreService implements SavedMatchService {
        private final Game gameToReturn;
        private final String hostNicknameToReturn;
        private String capturedGameId;
        private String lastSavedHostNickname;

        private RecordingRestoreService(Game gameToReturn, String hostNicknameToReturn) {
            this.gameToReturn = gameToReturn;
            this.hostNicknameToReturn = hostNicknameToReturn;
        }

        @Override
        public boolean isPersistenceEnabled() {
            return true;
        }

        @Override
        public void initializeMatch(Game game, String hostNickname) {
            this.lastSavedHostNickname = hostNickname;
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
            this.lastSavedHostNickname = hostNickname;
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
            this.lastSavedHostNickname = hostNickname;
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
            this.lastSavedHostNickname = hostNickname;
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
            this.lastSavedHostNickname = hostNickname;
        }

        @Override
        public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
            this.lastSavedHostNickname = hostNickname;
        }

        @Override
        public SavedMatchRestoreResult restoreMatch(String gameId) {
            this.capturedGameId = gameId;
            return new SavedMatchRestoreResult(this.gameToReturn, this.hostNicknameToReturn);
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

    private static Game restoredGame(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        for (int index = 0; index < nicknames.length; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], new Tribe()));
        }

        return new Game(
                77,
                players,
                new TurnState(),
                new TurnOrderTrack(List.of(players.get(0), players.get(1)), 0, 1, it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase.PLACING_TOTEMS),
                new Board(
                        TribeDeck.fromOrderedCards(new ArrayList<>()),
                        BuildingDeck.fromOrderedCards(new ArrayList<>()),
                        players
                ),
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static Game restoredPlacementGame(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        for (int index = 0; index < nicknames.length; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], new Tribe()));
        }

        Game game = DefaultGameFactory.getInstance().createGame(players);
        game.setup();
        return game;
    }
}
