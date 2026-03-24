package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.*;
import it.polimi.ingsoft.mesos.server.testing.GameStateAssertions;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedMatchRestoreFactoryTest {

    @Test
    void restoreRoundTripsMappedGameplaySnapshot() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        var factory = MesosTestFixtures.createTwoPlayerGameplayFactory();
        factory.createGame(MesosTestFixtures.createPlayers("Warmup-A", "Warmup-B"));
        Game game = factory.createGame(players);
        game.setup();

        game.moveTotem(players.get(0), "B");
        game.moveTotem(players.get(1), "C");
        game.chooseCard(players.get(0), MesosTestFixtures.findLowerRowCard(game, 10));

        SavedMatchAggregate aggregate = SavedMatchMapper.fromGame(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);
        org.junit.jupiter.api.Assertions.assertEquals("1", aggregate.gameId());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("C:Bob"),
                aggregate.offerSpaces().stream()
                        .filter(record -> record.occupiedByNickname() != null)
                        .sorted(java.util.Comparator.comparingInt(SavedMatchOfferSpaceRecord::positionIndex))
                        .map(record -> record.offerSpaceCode() + ":" + record.occupiedByNickname())
                        .toList()
        );
        Game restoredGame = SavedMatchRestoreFactory.restore(aggregate);

        GameStateAssertions.assertEquivalent(game, restoredGame);
    }

    @Test
    void restoreRejectsNonNumericGameIdUnknownOfferSpaceAndMissingRuntimeBuilding() {
        Game baseGame = MesosTestFixtures.createTwoPlayerGameplayFactory().createGame(MesosTestFixtures.createPlayers("Alice", "Bob"));
        baseGame.setup();
        SavedMatchAggregate baseAggregate = SavedMatchMapper.fromGame(baseGame, "Alice", SavedMatchCheckpointType.GAME_INITIALIZED);

        SavedMatchAggregate invalidGameId = copyAggregate(
                baseAggregate,
                "game-1",
                baseAggregate.offerSpaces(),
                baseAggregate.playerCardRuntimeStates()
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchRestoreFactory.restore(invalidGameId));

        SavedMatchAggregate invalidOfferSpace = copyAggregate(
                baseAggregate,
                baseAggregate.gameId(),
                List.of(new SavedMatchOfferSpaceRecord("Z", 99, null)),
                baseAggregate.playerCardRuntimeStates()
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchRestoreFactory.restore(invalidOfferSpace));

        SavedMatchAggregate missingRuntimeBuilding = copyAggregate(
                baseAggregate,
                baseAggregate.gameId(),
                baseAggregate.offerSpaces(),
                List.of(new SavedMatchPlayerCardRuntimeStateRecord("Alice", 999, "counter", 1, null))
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchRestoreFactory.restore(missingRuntimeBuilding));
    }

    @Test
    void restoreRejectsAggregateThatFailsConsistencyValidation() {
        SavedMatchAggregate invalidAggregate = new SavedMatchAggregate(
                "1",
                "Alice",
                2,
                it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus.IN_PROGRESS,
                it.polimi.ingsoft.mesos.server.model.enums.Era.first,
                it.polimi.ingsoft.mesos.server.model.enums.GameStatus.IN_PROGRESS,
                1,
                it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase.PLACING_TOTEMS,
                it.polimi.ingsoft.mesos.server.model.enums.TurnPhase.TURN_COMPLETED,
                null,
                0,
                SavedMatchCheckpointType.TURN_STARTED,
                List.of(
                        new SavedMatchPlayerRecord(0, "Alice", TotemColor.RED, 0, 0, false, null, null),
                        new SavedMatchPlayerRecord(1, "Alice", TotemColor.BLUE, 0, 0, false, null, null)
                ),
                List.of(
                        new SavedMatchTurnOrderRecord(0, "Alice"),
                        new SavedMatchTurnOrderRecord(1, "Alice")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchRestoreFactory.restore(invalidAggregate));
    }

    @Test
    void restoreRebuildsOwnedBuildingsAndRuntimeState() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        Game game = MesosTestFixtures.createTwoPlayerGameplayFactory().createGame(players);
        game.setup();
        players.get(1).gainFood(10);

        game.moveTotem(players.get(0), "B");
        game.moveTotem(players.get(1), "C");
        game.chooseCard(players.get(0), MesosTestFixtures.findLowerRowCard(game, 10));
        game.chooseCard(players.get(1), MesosTestFixtures.findUpperRowCard(game, 97));

        SetForFood setForFood = (SetForFood) players.get(1).getTribe().getBuildings().getFirst();
        setForFood.restoreRuntimeState(Map.of(
                "HUNTER", 1,
                "BUILDER", 2,
                "GATHERER", 0,
                "ARTIST", 1,
                "INVENTOR", 0,
                "SHAMAN", 3
        ));

        SavedMatchAggregate aggregate = SavedMatchMapper.fromGame(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);
        Game restoredGame = SavedMatchRestoreFactory.restore(aggregate);
        Player restoredBob = restoredGame.getPlayers().get(1);

        assertIterableEquals(
                players.get(1).getTribe().getBuildings().stream().map(BuildingCard::getId).toList(),
                restoredBob.getTribe().getBuildings().stream().map(BuildingCard::getId).toList()
        );
        assertEquals(
                players.get(1).getTribe().getBuildings().getFirst().exportRuntimeState(),
                restoredBob.getTribe().getBuildings().getFirst().exportRuntimeState()
        );
    }

    private static SavedMatchAggregate copyAggregate(
            SavedMatchAggregate base,
            String gameId,
            List<SavedMatchOfferSpaceRecord> offerSpaces,
            List<SavedMatchPlayerCardRuntimeStateRecord> playerCardRuntimeStates
    ) {
        return new SavedMatchAggregate(
                gameId,
                base.hostNickname(),
                base.playerCount(),
                base.status(),
                base.currentEra(),
                base.gameStatus(),
                base.roundNumber(),
                base.roundPhase(),
                base.turnPhase(),
                base.activePlayerNickname(),
                base.currentPlayerIndex(),
                base.lastCheckpointType(),
                base.players(),
                base.turnOrder(),
                base.turnChoices(),
                offerSpaces,
                base.startingBlocks(),
                base.boardCards(),
                base.playerCards(),
                playerCardRuntimeStates
        );
    }
}
