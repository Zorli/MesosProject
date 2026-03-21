package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedMatchConsistencyCheckerTest {

    @Test
    void validateAcceptsConsistentAggregate() {
        assertDoesNotThrow(() -> SavedMatchConsistencyChecker.validate(validAggregate()));
    }

    @Test
    void validateRejectsDuplicatePlayerAndUnknownActivePlayer() {
        SavedMatchAggregate duplicatePlayers = aggregateWith(
                List.of(
                        new SavedMatchPlayerRecord(0, "Alice", TotemColor.RED, 0, 0, false, null, null),
                        new SavedMatchPlayerRecord(1, "Alice", TotemColor.BLUE, 0, 0, false, null, null)
                ),
                validAggregate().turnOrder(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicatePlayers));

        SavedMatchAggregate unknownActivePlayer = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                "Carol",
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownActivePlayer));
    }

    @Test
    void validateRejectsUnknownOrDuplicateTurnOrderAndOutOfBoundsIndex() {
        SavedMatchAggregate unknownTurnOrderPlayer = aggregateWith(
                validAggregate().players(),
                List.of(
                        new SavedMatchTurnOrderRecord(0, "Alice"),
                        new SavedMatchTurnOrderRecord(1, "Carol")
                ),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownTurnOrderPlayer));

        SavedMatchAggregate duplicateTurnOrderPlayer = aggregateWith(
                validAggregate().players(),
                List.of(
                        new SavedMatchTurnOrderRecord(0, "Alice"),
                        new SavedMatchTurnOrderRecord(1, "Alice")
                ),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateTurnOrderPlayer));

        SavedMatchAggregate invalidCurrentPlayerIndex = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                null,
                2
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(invalidCurrentPlayerIndex));
    }

    @Test
    void validateRejectsDuplicateCardIdsAndUnknownPlayerOwnedCard() {
        SavedMatchAggregate duplicateCardIds = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                List.of(
                        new SavedMatchBoardCardRecord(SavedMatchBoardCardZone.UPPER_TRIBE, 0, 10),
                        new SavedMatchBoardCardRecord(SavedMatchBoardCardZone.LOWER_TRIBE, 0, 10)
                ),
                List.of(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateCardIds));

        SavedMatchAggregate unknownPlayerOwnedCard = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                List.of(),
                List.of(new SavedMatchPlayerCardRecord("Carol", SavedMatchPlayerCardZone.TRIBE_CHARACTER, 0, 20)),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownPlayerOwnedCard));
    }

    private static SavedMatchAggregate validAggregate() {
        return new SavedMatchAggregate(
                "1",
                "Alice",
                2,
                SavedMatchStatus.IN_PROGRESS,
                Era.first,
                GameStatus.IN_PROGRESS,
                1,
                RoundPhase.PLACING_TOTEMS,
                TurnPhase.TURN_COMPLETED,
                null,
                0,
                SavedMatchCheckpointType.TURN_STARTED,
                List.of(
                        new SavedMatchPlayerRecord(0, "Alice", TotemColor.RED, 0, 0, false, null, null),
                        new SavedMatchPlayerRecord(1, "Bob", TotemColor.BLUE, 0, 0, false, null, null)
                ),
                List.of(
                        new SavedMatchTurnOrderRecord(0, "Alice"),
                        new SavedMatchTurnOrderRecord(1, "Bob")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static SavedMatchAggregate aggregateWith(
            List<SavedMatchPlayerRecord> players,
            List<SavedMatchTurnOrderRecord> turnOrder,
            List<SavedMatchBoardCardRecord> boardCards,
            List<SavedMatchPlayerCardRecord> playerCards,
            String activePlayerNickname,
            int currentPlayerIndex
    ) {
        SavedMatchAggregate base = validAggregate();
        return new SavedMatchAggregate(
                base.gameId(),
                base.hostNickname(),
                base.playerCount(),
                base.status(),
                base.currentEra(),
                base.gameStatus(),
                base.roundNumber(),
                base.roundPhase(),
                base.turnPhase(),
                activePlayerNickname,
                currentPlayerIndex,
                base.lastCheckpointType(),
                players,
                turnOrder,
                base.turnChoices(),
                base.offerSpaces(),
                base.startingBlocks(),
                boardCards,
                playerCards,
                base.playerCardRuntimeStates()
        );
    }
}
