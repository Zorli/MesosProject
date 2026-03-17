package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresSavedMatchRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void failedSaveRollsBackAndPreservesPreviousPersistedState() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_rollback", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        PostgresSavedMatchRepository repository = new PostgresSavedMatchRepository(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );

        Game game = createGame(2);
        game.setup();
        SavedMatchAggregate originalAggregate = SavedMatchMapper.fromGame(game, "Alice", SavedMatchCheckpointType.GAME_INITIALIZED);
        repository.save(originalAggregate);

        SavedMatchAggregate brokenAggregate = new SavedMatchAggregate(
                originalAggregate.gameId(),
                originalAggregate.hostNickname(),
                originalAggregate.playerCount(),
                originalAggregate.status(),
                originalAggregate.currentEra(),
                originalAggregate.gameStatus(),
                originalAggregate.roundNumber(),
                originalAggregate.roundPhase(),
                originalAggregate.turnPhase(),
                originalAggregate.activePlayerNickname(),
                originalAggregate.currentPlayerIndex(),
                SavedMatchCheckpointType.TURN_STARTED,
                originalAggregate.players(),
                originalAggregate.turnOrder(),
                originalAggregate.turnChoices(),
                List.of(new SavedMatchOfferSpaceRecord("B", 1, "Ghost")),
                originalAggregate.startingBlocks(),
                originalAggregate.boardCards(),
                originalAggregate.playerCards(),
                originalAggregate.playerCardRuntimeStates()
        );

        assertThrows(RuntimeException.class, () -> repository.save(brokenAggregate));
        assertEquals(originalAggregate, repository.load(originalAggregate.gameId()));
    }

    private static Game createGame(int playerCount) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        String[] nicknames = new String[]{"Alice", "Bob", "Carol", "Dave", "Eve"};
        for (int index = 0; index < playerCount; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], null));
        }
        return DefaultGameFactory.getInstance().createGame(players);
    }
}
