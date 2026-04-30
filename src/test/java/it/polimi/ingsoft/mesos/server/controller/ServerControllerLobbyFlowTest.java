package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ServerControllerLobbyFlowTest {

    @Test
    void createJoinAndExitNotifySessionsFromRegistry() {
        ServerController controller = new ServerController();
        RecordingConnection aliceConnection = new RecordingConnection();
        RecordingConnection bobConnection = new RecordingConnection();
        String aliceSessionId = controller.registerConnection(aliceConnection);
        String bobSessionId = controller.registerConnection(bobConnection);
        controller.authenticate(aliceSessionId, "Alice");
        controller.authenticate(bobSessionId, "Bob");

        controller.createLobby(aliceSessionId, 2);
        controller.joinLobby(bobSessionId, 1);
        controller.exitLobby(bobSessionId);

        PreMatchUpdatePayload created = aliceConnection.updateAt(0);
        assertEquals(PreMatchEventType.LOBBY_CREATED, created.event);
        assertEquals(List.of("Alice"), created.players);

        PreMatchUpdatePayload joinedForAlice = aliceConnection.updateAt(1);
        PreMatchUpdatePayload joinedForBob = bobConnection.updateAt(0);
        assertEquals(PreMatchEventType.LOBBY_JOINED, joinedForAlice.event);
        assertEquals(PreMatchEventType.LOBBY_JOINED, joinedForBob.event);
        assertEquals(List.of("Alice", "Bob"), joinedForAlice.players);
        assertEquals("Alice", joinedForBob.hostNickname);

        PreMatchUpdatePayload leftForAlice = aliceConnection.updateAt(2);
        PreMatchUpdatePayload leftForBob = bobConnection.updateAt(1);
        assertEquals(PreMatchEventType.LOBBY_LEFT, leftForAlice.event);
        assertEquals(PreMatchEventType.LOBBY_LEFT, leftForBob.event);
        assertEquals(List.of("Alice"), leftForAlice.players);
        assertEquals(1, leftForBob.lobbyId);
    }

    @Test
    void startLobbyMovesAllLobbySessionsIntoTheCreatedGame() {
        ServerController controller = new ServerController(new RecordingGameFactory());
        RecordingConnection aliceConnection = new RecordingConnection();
        RecordingConnection bobConnection = new RecordingConnection();
        String aliceSessionId = controller.registerConnection(aliceConnection);
        String bobSessionId = controller.registerConnection(bobConnection);
        controller.authenticate(aliceSessionId, "Alice");
        controller.authenticate(bobSessionId, "Bob");
        controller.createLobby(aliceSessionId, 2);
        controller.joinLobby(bobSessionId, 1);

        GameOrchestrator orchestrator = controller.startLobby(aliceSessionId);

        assertSame(orchestrator, controller.gameOf(aliceSessionId));
        assertSame(orchestrator, controller.gameOf(bobSessionId));
        PreMatchUpdatePayload startedForAlice = aliceConnection.lastUpdate();
        PreMatchUpdatePayload startedForBob = bobConnection.lastUpdate();
        assertEquals(PreMatchEventType.LOBBY_STARTED, startedForAlice.event);
        assertEquals(PreMatchEventType.LOBBY_STARTED, startedForBob.event);
    }

    private static final class RecordingConnection implements PingPongConnection, Listener {
        private final List<Object> updates = new ArrayList<>();

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

        private PreMatchUpdatePayload updateAt(int index) {
            return assertInstanceOf(PreMatchUpdatePayload.class, this.updates.get(index));
        }

        private PreMatchUpdatePayload lastUpdate() {
            return this.updateAt(this.updates.size() - 1);
        }
    }

    private static final class RecordingGameFactory implements GameFactory {
        @Override
        public Game createGame(List<Player> players) {
            return new Game(
                    1,
                    players,
                    new TurnState(),
                    new TurnOrderTrack(players, 0, 1, it.polimi.ingsoft.mesos.server.model.game.RoundPhase.PLACING_TOTEMS),
                    new Board(
                            TribeDeck.fromOrderedCards(new ArrayList<>()),
                            BuildingDeck.fromOrderedCards(new ArrayList<>()),
                            players
                    ),
                    Era.first,
                    GameStatus.SETUP
            );
        }
    }
}
