package it.polimi.ingsoft.mesos.server.model.Orchestration;

import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.SavedMatchService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameOrchestratorTest {

    @Test
    void restoredGameConstructorReusesExistingAggregate() {
        ArrayList<Player> players = new ArrayList<>();
        players.add(new Player("Alice", 0, 0, TotemColor.RED, new Tribe()));
        players.add(new Player("Bob", 0, 0, TotemColor.BLUE, new Tribe()));

        Game restoredGame = DefaultGameFactory.getInstance().createGame(players);

        GameOrchestrator orchestrator = new GameOrchestrator(restoredGame);

        assertSame(restoredGame, orchestrator.getGame());
        assertIterableEquals(restoredGame.getPlayers(), orchestrator.getPlayers());
    }

    @Test
    void setupMatchPersistsInitialCheckpoint() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();

        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                savedMatchService
        );

        orchestrator.setupMatch();

        assertEquals(1, savedMatchService.initializeCalls);
        assertSame(orchestrator.getGame(), savedMatchService.lastGame);
        assertEquals("Alice", savedMatchService.lastHostNickname);
        assertEquals(RoundPhase.PLACING_TOTEMS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
        assertTrue(orchestrator.isPersistenceEnabled());
    }

    @Test
    void moveTotemPersistsTurnStartedWhenPlacementPhaseCompletes() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();

        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                savedMatchService
        );
        orchestrator.setupMatch();

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");
        assertEquals(0, savedMatchService.turnStartedCalls);

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C");
        assertEquals(1, savedMatchService.turnStartedCalls);
        assertEquals(RoundPhase.PLAYER_DRAWS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static ArrayList<Player> createPlayers(int playerCount) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        String[] nicknames = new String[]{"Alice", "Bob", "Carol", "Dave", "Eve"};
        for (int index = 0; index < playerCount; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], new Tribe()));
        }
        return players;
    }

    private static final class RecordingSavedMatchService implements SavedMatchService {
        private int initializeCalls;
        private int turnStartedCalls;
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
        public Game restoreGame(String gameId) {
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
