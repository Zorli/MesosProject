package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.user.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static it.polimi.ingsoft.mesos.server.model.lobby.LobbyTestUsers.userWithNickname;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        Lobby firstLobby = manager.createLobby(userWithNickname("Alice"), 2);
        Lobby secondLobby = manager.createLobby(userWithNickname("Bob"), 3);

        assertEquals(1, firstLobby.getId());
        assertEquals(2, secondLobby.getId());
        assertTrue(manager.getLobby(1).isPresent());
        assertTrue(manager.getLobby(2).isPresent());
    }

    @Test
    void joinLobbySetsStatusToReadyWhenChosenCapacityIsReached() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(userWithNickname("Alice"), 2);

        manager.joinLobby(lobby.getId(), userWithNickname("Bob"));

        assertEquals(LobbyStatus.READY, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
        assertTrue(manager.canStart(lobby.getId()));
    }

    @Test
    void removeHostClosesStandardLobbyWhenAlone() {
        LobbyManager manager = new LobbyManager();
        User host = userWithNickname("Alice");
        Lobby lobby = manager.createLobby(host, 2);

        manager.removeUser(lobby.getId(), host);

        assertEquals(LobbyStatus.CLOSED, lobby.getStatus());
        assertTrue(manager.getLobby(lobby.getId()).isEmpty());
    }

    @Test
    void removeHostTransfersHostAndReturnsReadyLobbyToWaitingWhenBelowCapacity() {
        LobbyManager manager = new LobbyManager();
        User host = userWithNickname("Alice");
        User bob = userWithNickname("Bob");
        Lobby lobby = manager.createLobby(host, 2);
        manager.joinLobby(lobby.getId(), bob);

        manager.removeUser(lobby.getId(), host);

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals("Bob", updatedLobby.getHostNickname());
        assertEquals(LobbyStatus.WAITING, updatedLobby.getStatus());
        assertFalse(updatedLobby.canStart());
    }

    @Test
    void createGameFromLobbyConvertsUsersToPlayersWithDistinctTotems() {
        RecordingGameFactory gameFactory = new RecordingGameFactory();
        LobbyManager manager = new LobbyManager(gameFactory);
        Lobby lobby = manager.createLobby(userWithNickname("Alice"), 3);
        manager.joinLobby(lobby.getId(), userWithNickname("Bob"));
        manager.joinLobby(lobby.getId(), userWithNickname("Carol"));

        GameOrchestrator gameOrchestrator = manager.createGameFromLobby(lobby.getId());

        assertInstanceOf(GameOrchestrator.class, gameOrchestrator);
        assertEquals(1, gameFactory.createGameCalls);
        assertEquals(
                Set.of("Alice", "Bob", "Carol"),
                new HashSet<>(gameFactory.capturedPlayers.stream().map(Player::getNickname).toList())
        );
        assertEquals(
                gameFactory.capturedPlayers.size(),
                new HashSet<>(gameFactory.capturedPlayers.stream().map(Player::getTotem).toList()).size()
        );
        assertTrue(gameFactory.capturedPlayers.stream().allMatch(player -> player.getFood() == 0));
        assertTrue(gameFactory.capturedPlayers.stream().allMatch(player -> player.getPrestigePoints() == 0));
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
    }

    @Test
    void createGameFromLobbyRejectsNonReadyAndResumableLobbies() {
        LobbyManager manager = new LobbyManager();
        Lobby waitingLobby = manager.createLobby(userWithNickname("Carol"), 2);
        Lobby resumableLobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(resumableLobby.getId(), userWithNickname("Alice"));
        manager.joinLobby(resumableLobby.getId(), userWithNickname("Bob"));

        assertThrows(IllegalStateException.class, () -> manager.createGameFromLobby(waitingLobby.getId()));
        assertThrows(IllegalStateException.class, () -> manager.createGameFromLobby(resumableLobby.getId()));
    }

    @Test
    void createResumeLobbyReconnectsOriginalUsersAndResumeStartsMatch() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), userWithNickname("Alice"));
        manager.joinLobby(lobby.getId(), userWithNickname("Bob"));
        RecordingRestoreService restoreService = new RecordingRestoreService(restoredGame("Alice", "Bob"), "Alice");

        GameOrchestrator gameOrchestrator = manager.resumeGameFromLobby(lobby.getId(), restoreService);

        assertEquals("game-77", restoreService.capturedGameId);
        assertEquals(LobbyStatus.STARTED, manager.getLobby(lobby.getId()).orElseThrow().getStatus());
        assertEquals(List.of("Alice", "Bob"), gameOrchestrator.getPlayers().stream().map(Player::getNickname).toList());
    }

    @Test
    void removeLastUserFromResumableLobbyKeepsLobbyOpen() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        User alice = userWithNickname("Alice");
        manager.joinLobby(lobby.getId(), alice);

        manager.removeUser(lobby.getId(), alice);

        Lobby updatedLobby = manager.getLobby(lobby.getId()).orElseThrow();
        assertEquals(LobbyStatus.RESUMABLE, updatedLobby.getStatus());
        assertTrue(updatedLobby.getUsers().isEmpty());
    }

    @Test
    void joinAndRemoveUserRejectInvalidInputsAndDuplicateMembership() {
        LobbyManager manager = new LobbyManager();
        Lobby firstLobby = manager.createLobby(userWithNickname("Alice"), 2);
        Lobby secondLobby = manager.createLobby(userWithNickname("Carol"), 2);
        User bob = userWithNickname("Bob");
        manager.joinLobby(firstLobby.getId(), bob);

        assertThrows(IllegalArgumentException.class, () -> manager.joinLobby(firstLobby.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> manager.joinLobby(999, userWithNickname("Dave")));
        assertThrows(IllegalStateException.class, () -> manager.joinLobby(secondLobby.getId(), bob));
        assertThrows(IllegalArgumentException.class, () -> manager.removeUser(firstLobby.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> manager.removeUser(999, userWithNickname("Bob")));
    }

    @Test
    void listOpenLobbiesExcludesClosedAndStartedOnes() {
        LobbyManager manager = new LobbyManager(new RecordingGameFactory());

        Lobby waitingLobby = manager.createLobby(userWithNickname("Alice"), 3);
        Lobby startedLobby = manager.createLobby(userWithNickname("Bob"), 2);
        manager.joinLobby(startedLobby.getId(), userWithNickname("Carol"));
        manager.createGameFromLobby(startedLobby.getId());

        Lobby closedLobby = manager.createLobby(userWithNickname("Dave"), 2);
        manager.removeUser(closedLobby.getId(), userWithNickname("Dave"));

        List<Lobby> openLobbies = manager.listOpenLobbies();

        assertEquals(1, openLobbies.size());
        assertEquals(waitingLobby.getId(), openLobbies.getFirst().getId());
    }

    @Test
    void toStringIncludesTrackedLobbyCount() {
        LobbyManager manager = new LobbyManager();
        manager.createLobby(userWithNickname("Alice"), 2);
        manager.createLobby(userWithNickname("Bob"), 2);

        String description = manager.toString();

        assertTrue(description.contains("nextLobbyId=3"));
        assertTrue(description.contains("lobbiesCount=2"));
    }

    private static final class RecordingGameFactory implements GameFactory {
        private int createGameCalls;
        private ArrayList<Player> capturedPlayers;

        @Override
        public Game createGame(List<Player> players) {
            this.createGameCalls++;
            this.capturedPlayers = new ArrayList<>(players);
            return null;
        }
    }

    private static final class RecordingRestoreService implements SavedMatchService {
        private final Game gameToReturn;
        private final String hostNicknameToReturn;
        private String capturedGameId;

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
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
        }

        @Override
        public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
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
                new TurnOrderTrack(List.of(players.get(0), players.get(1)), 0, 1, it.polimi.ingsoft.mesos.server.model.game.RoundPhase.PLACING_TOTEMS),
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
