package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.user.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LobbyManagerResumeFlowTest {

    @Test
    void resumeGameFromLobbyUsesPersistedHostForSubsequentCheckpoints() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), user("Bob"));
        manager.joinLobby(lobby.getId(), user("Alice"));

        RecordingRestoreService restoreService = new RecordingRestoreService(restoredPlacementGame(), "Alice");

        GameOrchestrator orchestrator = manager.resumeGameFromLobby(lobby.getId(), restoreService);

        assertEquals("game-77", restoreService.capturedGameId);
        assertEquals("Bob", manager.getLobby(lobby.getId()).orElseThrow().getHostNickname());

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");
        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C");

        assertEquals("Alice", restoreService.lastSavedHostNickname);
        assertEquals(1, restoreService.turnStartedCalls);
    }

    @Test
    void resumeGameFromLobbyRejectsRestoredRosterMismatch() {
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        manager.joinLobby(lobby.getId(), user("Alice"));
        manager.joinLobby(lobby.getId(), user("Bob"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> manager.resumeGameFromLobby(
                        lobby.getId(),
                        new RecordingRestoreService(restoredGame("Alice", "Carol"), "Alice")
                )
        );

        assertEquals("Restored match roster does not match resumable lobby roster", exception.getMessage());
    }

    private static User user(String nickname) {
        return new User(nickname);
    }

    private static Game restoredPlacementGame() {
        Game game = restoredGame("Alice", "Bob");
        game.setup();
        return game;
    }

    private static Game restoredGame(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] colors = TotemColor.values();
        for (int index = 0; index < nicknames.length; index++) {
            players.add(new Player(nicknames[index], 0, 0, colors[index]));
        }
        return DefaultGameFactory.getInstance().createGame(players);
    }

    private static final class RecordingRestoreService implements SavedMatchService {
        private final Game restoredGame;
        private final String hostNickname;
        private String capturedGameId;
        private String lastSavedHostNickname;
        private int turnStartedCalls;

        private RecordingRestoreService(Game restoredGame, String hostNickname) {
            this.restoredGame = restoredGame;
            this.hostNickname = hostNickname;
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
            this.turnStartedCalls++;
            this.lastSavedHostNickname = hostNickname;
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
            return new SavedMatchRestoreResult(this.restoredGame, this.hostNickname);
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
