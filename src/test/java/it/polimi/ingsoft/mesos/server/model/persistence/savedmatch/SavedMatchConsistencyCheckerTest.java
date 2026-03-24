package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.*;
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
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicatePlayers));

        SavedMatchAggregate unknownActivePlayer = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
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
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
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
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateTurnOrderPlayer));

        SavedMatchAggregate invalidCurrentPlayerIndex = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
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
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                List.of(
                        new SavedMatchBoardCardRecord(SavedMatchBoardCardZone.UPPER_TRIBE, 0, 10),
                        new SavedMatchBoardCardRecord(SavedMatchBoardCardZone.LOWER_TRIBE, 0, 10)
                ),
                List.of(),
                List.of(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateCardIds));

        SavedMatchAggregate unknownPlayerOwnedCard = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                List.of(),
                List.of(new SavedMatchPlayerCardRecord("Carol", SavedMatchPlayerCardZone.TRIBE_CHARACTER, 0, 20)),
                List.of(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownPlayerOwnedCard));
    }

    @Test
    void validateRejectsMismatchedPlayerCountAndUnknownHostNickname() {
        SavedMatchAggregate wrongPlayerCount = new SavedMatchAggregate(
                validAggregate().gameId(),
                validAggregate().hostNickname(),
                3,
                validAggregate().status(),
                validAggregate().currentEra(),
                validAggregate().gameStatus(),
                validAggregate().roundNumber(),
                validAggregate().roundPhase(),
                validAggregate().turnPhase(),
                validAggregate().activePlayerNickname(),
                validAggregate().currentPlayerIndex(),
                validAggregate().lastCheckpointType(),
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates()
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(wrongPlayerCount));

        SavedMatchAggregate unknownHost = new SavedMatchAggregate(
                validAggregate().gameId(),
                "Carol",
                validAggregate().playerCount(),
                validAggregate().status(),
                validAggregate().currentEra(),
                validAggregate().gameStatus(),
                validAggregate().roundNumber(),
                validAggregate().roundPhase(),
                validAggregate().turnPhase(),
                validAggregate().activePlayerNickname(),
                validAggregate().currentPlayerIndex(),
                validAggregate().lastCheckpointType(),
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates()
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownHost));
    }

    @Test
    void validateRejectsInvalidTurnChoiceOccupancyAndIndexState() {
        SavedMatchAggregate duplicateTurnChoicePlayer = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                List.of(
                        new SavedMatchTurnChoiceRecord("Alice", 1, 0),
                        new SavedMatchTurnChoiceRecord("Alice", 0, 1)
                ),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateTurnChoicePlayer));

        SavedMatchAggregate unknownTurnChoicePlayer = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                List.of(new SavedMatchTurnChoiceRecord("Carol", 1, 0)),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownTurnChoicePlayer));

        SavedMatchAggregate negativeCurrentPlayerIndex = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                -1
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(negativeCurrentPlayerIndex));

        SavedMatchAggregate activePlayerDuringPlacement = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                "Alice",
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(activePlayerDuringPlacement));
    }

    @Test
    void validateRejectsInvalidOccupancyAndPositionDuplicates() {
        SavedMatchAggregate unknownOfferOccupant = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                List.of(new SavedMatchOfferSpaceRecord("B", 1, "Carol")),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(unknownOfferOccupant));

        SavedMatchAggregate duplicatedOccupancy = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                List.of(new SavedMatchOfferSpaceRecord("B", 1, "Alice")),
                List.of(new SavedMatchStartingBlockRecord(0, 0, 0, "Alice")),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicatedOccupancy));

        SavedMatchAggregate duplicateBoardPosition = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                List.of(
                        new SavedMatchBoardCardRecord(SavedMatchBoardCardZone.UPPER_TRIBE, 0, 10),
                        new SavedMatchBoardCardRecord(SavedMatchBoardCardZone.UPPER_TRIBE, 0, 11)
                ),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateBoardPosition));

        SavedMatchAggregate duplicatePlayerCardPosition = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                List.of(
                        new SavedMatchPlayerCardRecord("Alice", SavedMatchPlayerCardZone.TRIBE_BUILDING, 0, 20),
                        new SavedMatchPlayerCardRecord("Alice", SavedMatchPlayerCardZone.TRIBE_BUILDING, 0, 21)
                ),
                validAggregate().playerCardRuntimeStates(),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicatePlayerCardPosition));
    }

    @Test
    void validateRejectsInvalidRuntimeStateAndCompletedStatusMismatch() {
        SavedMatchAggregate runtimeStateForUnknownCard = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                List.of(new SavedMatchPlayerCardRuntimeStateRecord("Alice", 44, "counter", 1, null)),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(runtimeStateForUnknownCard));

        SavedMatchAggregate duplicateRuntimeState = aggregateWith(
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                List.of(new SavedMatchPlayerCardRecord("Alice", SavedMatchPlayerCardZone.TRIBE_BUILDING, 0, 44)),
                List.of(
                        new SavedMatchPlayerCardRuntimeStateRecord("Alice", 44, "counter", 1, null),
                        new SavedMatchPlayerCardRuntimeStateRecord("Alice", 44, "counter", 2, null)
                ),
                null,
                0
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(duplicateRuntimeState));

        SavedMatchAggregate completedButInProgressGame = new SavedMatchAggregate(
                validAggregate().gameId(),
                validAggregate().hostNickname(),
                validAggregate().playerCount(),
                SavedMatchStatus.COMPLETED,
                validAggregate().currentEra(),
                GameStatus.IN_PROGRESS,
                validAggregate().roundNumber(),
                validAggregate().roundPhase(),
                validAggregate().turnPhase(),
                validAggregate().activePlayerNickname(),
                validAggregate().currentPlayerIndex(),
                validAggregate().lastCheckpointType(),
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates()
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(completedButInProgressGame));

        SavedMatchAggregate finishedOutsideFinalScoring = new SavedMatchAggregate(
                validAggregate().gameId(),
                validAggregate().hostNickname(),
                validAggregate().playerCount(),
                SavedMatchStatus.COMPLETED,
                validAggregate().currentEra(),
                GameStatus.FINISHED,
                validAggregate().roundNumber(),
                RoundPhase.RESETTING_BOARD,
                validAggregate().turnPhase(),
                validAggregate().activePlayerNickname(),
                validAggregate().currentPlayerIndex(),
                validAggregate().lastCheckpointType(),
                validAggregate().players(),
                validAggregate().turnOrder(),
                validAggregate().turnChoices(),
                validAggregate().offerSpaces(),
                validAggregate().startingBlocks(),
                validAggregate().boardCards(),
                validAggregate().playerCards(),
                validAggregate().playerCardRuntimeStates()
        );
        assertThrows(SavedMatchSnapshotConsistencyException.class, () -> SavedMatchConsistencyChecker.validate(finishedOutsideFinalScoring));
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
            List<SavedMatchTurnChoiceRecord> turnChoices,
            List<SavedMatchOfferSpaceRecord> offerSpaces,
            List<SavedMatchStartingBlockRecord> startingBlocks,
            List<SavedMatchBoardCardRecord> boardCards,
            List<SavedMatchPlayerCardRecord> playerCards,
            List<SavedMatchPlayerCardRuntimeStateRecord> playerCardRuntimeStates,
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
                turnChoices,
                offerSpaces,
                startingBlocks,
                boardCards,
                playerCards,
                playerCardRuntimeStates
        );
    }
}
