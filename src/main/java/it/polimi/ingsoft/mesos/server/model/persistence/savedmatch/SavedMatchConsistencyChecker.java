package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates normalized saved-match aggregates before they are stored or restored.
 */
final class SavedMatchConsistencyChecker {
    /**
     * Prevents instantiation of the validation utility class.
     */
    private SavedMatchConsistencyChecker() {
    }

    /**
     * Validates the aggregate and throws when invariants required for restore are broken.
     *
     * @param aggregate aggregate to validate
     */
    static void validate(SavedMatchAggregate aggregate) {
        if (aggregate.playerCount() != aggregate.players().size()) {
            throw new SavedMatchSnapshotConsistencyException("Saved player count does not match persisted players");
        }

        Set<String> playerNicknames = new HashSet<>();
        for (SavedMatchPlayerRecord player : aggregate.players()) {
            if (!playerNicknames.add(player.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate player nickname in saved match: " + player.nickname()
                );
            }
        }

        if (!playerNicknames.contains(aggregate.hostNickname())) {
            throw new SavedMatchSnapshotConsistencyException(
                    "Host nickname is not part of the saved match: " + aggregate.hostNickname()
            );
        }

        if (aggregate.activePlayerNickname() != null && !playerNicknames.contains(aggregate.activePlayerNickname())) {
            throw new SavedMatchSnapshotConsistencyException(
                    "Active player is not part of the saved match: " + aggregate.activePlayerNickname()
            );
        }

        if (aggregate.currentPlayerIndex() < 0) {
            throw new SavedMatchSnapshotConsistencyException("Current player index cannot be negative");
        }

        Set<String> turnOrderNicknames = new HashSet<>();
        for (SavedMatchTurnOrderRecord turnOrderRecord : aggregate.turnOrder()) {
            if (!playerNicknames.contains(turnOrderRecord.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Turn-order player is not part of the saved match: " + turnOrderRecord.nickname()
                );
            }
            if (!turnOrderNicknames.add(turnOrderRecord.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate player in turn order: " + turnOrderRecord.nickname()
                );
            }
        }

        if (!aggregate.turnOrder().isEmpty()
                && aggregate.currentPlayerIndex() >= aggregate.turnOrder().size()) {
            throw new SavedMatchSnapshotConsistencyException("Current player index exceeds turn-order size");
        }

        Set<String> turnChoiceNicknames = new HashSet<>();
        for (SavedMatchTurnChoiceRecord turnChoiceRecord : aggregate.turnChoices()) {
            if (!playerNicknames.contains(turnChoiceRecord.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Turn-choice player is not part of the saved match: " + turnChoiceRecord.nickname()
                );
            }
            if (!turnChoiceNicknames.add(turnChoiceRecord.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate player in turn choices: " + turnChoiceRecord.nickname()
                );
            }
        }

        Set<String> occupiedPlayers = new HashSet<>();
        for (SavedMatchOfferSpaceRecord offerSpaceRecord : aggregate.offerSpaces()) {
            validateOccupantReference(playerNicknames, offerSpaceRecord.occupiedByNickname(), "Offer-space player is not part of the saved match: ");
            if (offerSpaceRecord.occupiedByNickname() != null && !occupiedPlayers.add(offerSpaceRecord.occupiedByNickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Player occupies multiple saved board positions: " + offerSpaceRecord.occupiedByNickname()
                );
            }
        }

        for (SavedMatchStartingBlockRecord startingBlockRecord : aggregate.startingBlocks()) {
            validateOccupantReference(playerNicknames, startingBlockRecord.occupiedByNickname(), "Starting-block player is not part of the saved match: ");
            if (startingBlockRecord.occupiedByNickname() != null && !occupiedPlayers.add(startingBlockRecord.occupiedByNickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Player occupies multiple saved board positions: " + startingBlockRecord.occupiedByNickname()
                );
            }
        }

        Set<Integer> cardIds = new HashSet<>();
        Map<String, Set<Integer>> boardPositionsByZone = new HashMap<>();
        for (SavedMatchBoardCardRecord boardCardRecord : aggregate.boardCards()) {
            if (!cardIds.add(boardCardRecord.cardId())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate card id detected across persisted board state: " + boardCardRecord.cardId()
                );
            }
            if (!boardPositionsByZone
                    .computeIfAbsent(boardCardRecord.cardZone().name(), ignored -> new HashSet<>())
                    .add(boardCardRecord.positionIndex())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate board position inside zone "
                                + boardCardRecord.cardZone() + ": " + boardCardRecord.positionIndex()
                );
            }
        }

        Map<String, Set<Integer>> playerPositionsByZone = new HashMap<>();
        for (SavedMatchPlayerCardRecord playerCardRecord : aggregate.playerCards()) {
            if (!playerNicknames.contains(playerCardRecord.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Player-owned card references unknown player: " + playerCardRecord.nickname()
                );
            }
            if (!cardIds.add(playerCardRecord.cardId())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate card id detected across persisted match state: " + playerCardRecord.cardId()
                );
            }
            String zoneKey = playerCardRecord.nickname() + ":" + playerCardRecord.cardZone().name();
            if (!playerPositionsByZone.computeIfAbsent(zoneKey, ignored -> new HashSet<>()).add(playerCardRecord.positionIndex())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate player-card position inside zone " + zoneKey + ": " + playerCardRecord.positionIndex()
                );
            }
        }

        Set<String> runtimeStateKeys = new HashSet<>();
        Set<String> persistedPlayerCards = new HashSet<>();
        for (SavedMatchPlayerCardRecord playerCardRecord : aggregate.playerCards()) {
            persistedPlayerCards.add(playerCardRecord.nickname() + ":" + playerCardRecord.cardId());
        }
        for (SavedMatchPlayerCardRuntimeStateRecord runtimeStateRecord : aggregate.playerCardRuntimeStates()) {
            if (!playerNicknames.contains(runtimeStateRecord.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Runtime-state row references unknown player: " + runtimeStateRecord.nickname()
                );
            }
            if (!persistedPlayerCards.contains(runtimeStateRecord.nickname() + ":" + runtimeStateRecord.cardId())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Runtime-state row references unknown player-owned card: "
                                + runtimeStateRecord.nickname() + ":" + runtimeStateRecord.cardId()
                );
            }
            String runtimeKey = runtimeStateRecord.nickname()
                    + ":" + runtimeStateRecord.cardId()
                    + ":" + runtimeStateRecord.stateKey();
            if (!runtimeStateKeys.add(runtimeKey)) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate runtime-state row detected: " + runtimeKey
                );
            }
        }

        if (aggregate.status() == SavedMatchStatus.COMPLETED && aggregate.gameStatus() != GameStatus.FINISHED) {
            throw new SavedMatchSnapshotConsistencyException("Completed saved match must reference a finished game");
        }

        if (aggregate.gameStatus() == GameStatus.FINISHED && aggregate.roundPhase() != RoundPhase.FINAL_SCORING) {
            throw new SavedMatchSnapshotConsistencyException("Finished game must be persisted in final-scoring round phase");
        }

        if (aggregate.roundPhase() == RoundPhase.PLACING_TOTEMS && aggregate.activePlayerNickname() != null) {
            throw new SavedMatchSnapshotConsistencyException("Placing-totems phase must not persist an active player nickname");
        }
    }

    private static void validateOccupantReference(Set<String> playerNicknames, String occupant, String messagePrefix) {
        if (occupant != null && !playerNicknames.contains(occupant)) {
            throw new SavedMatchSnapshotConsistencyException(messagePrefix + occupant);
        }
    }
}
