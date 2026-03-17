package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoOpSavedMatchServiceTest {

    @Test
    void noDbImplementationReportsPersistenceDisabledAndIgnoresSaveCalls() {
        SavedMatchService service = NoOpSavedMatchService.INSTANCE;
        Game game = createGame();
        game.setup();

        service.initializeMatch(game, "Alice");
        service.saveTurnStarted(game, "Alice");
        service.saveEraChanged(game, "Alice");
        service.saveTurnFinished(game, "Alice");
        service.completeMatch(game, "Alice");
        service.saveMatch(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);
        service.markPlayerOnline("1", "Alice", "session");
        service.markPlayerOffline("1", "Alice");

        assertFalse(service.isPersistenceEnabled());
        assertEquals(0, service.deleteExpiredInProgressMatches());
    }

    @Test
    void noDbImplementationRejectsRestore() {
        SavedMatchService service = NoOpSavedMatchService.INSTANCE;

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.restoreGame("game-1")
        );

        assertEquals("Saved-match restore is not available in no-db mode", exception.getMessage());
    }

    private static Game createGame() {
        ArrayList<Player> players = new ArrayList<>();
        players.add(new Player("Alice", 0, 0, TotemColor.RED, null));
        players.add(new Player("Bob", 0, 0, TotemColor.BLUE, null));
        return DefaultGameFactory.getInstance().createGame(players);
    }
}
