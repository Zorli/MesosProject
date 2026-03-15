package it.polimi.ingsoft.mesos.server.model.Orchestration;

import it.polimi.ingsoft.mesos.server.model.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GameOrchestratorIntegrationTest {

    @Test
    void createGameFromLobbyStoresGameCreatedByFactoryInsideOrchestrator() {
        RecordingGameFactory gameFactory = new RecordingGameFactory();
        Player host = playerWithNickname("Alice");
        Player secondPlayer = playerWithNickname("Bob");
        LobbyManager manager = new LobbyManager(gameFactory);
        Lobby lobby = manager.createLobby(host);
        manager.joinLobby(lobby.getId(), secondPlayer);
        List<Player> expectedPlayers = lobby.getPlayers();

        GameOrchestrator gameOrchestrator = manager.createGameFromLobby(lobby.getId());

        assertEquals(1, gameFactory.createGameCalls);
        assertEquals(expectedPlayers, gameFactory.capturedPlayers);
        assertNotSame(expectedPlayers, gameFactory.capturedPlayers);
        assertSame(gameFactory.gameToReturn, gameOrchestrator.game);
    }

    private static final class RecordingGameFactory implements GameFactory {
        private int createGameCalls;
        private ArrayList<Player> capturedPlayers;
        private final Game gameToReturn = new Game(
                42,
                new ArrayList<>(),
                new TurnState(),
                new TurnOrderTrack(),
                null,
                null,
                null
        );

        @Override
        public Game createGame(ArrayList<Player> players) {
            this.createGameCalls++;
            this.capturedPlayers = new ArrayList<>(players);
            return this.gameToReturn;
        }
    }

    private static Player playerWithNickname(String nickname) {
        return new Player(nickname, 0, 0, TotemColor.RED, null);
    }
}
