package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates normalized saved-match aggregates before they are stored or restored.
 */
final class SavedMatchConsistencyChecker {
    private SavedMatchConsistencyChecker() {
    }

    /**
     * Validates the aggregate and throws when invariants required for restore are broken.
     *
     * @param aggregate aggregate to validate
     */
    static void validate(SavedMatchAggregate aggregate) {
        Set<String> playerNicknames = new HashSet<>();
        for (SavedMatchPlayerRecord player : aggregate.players()) {
            if (!playerNicknames.add(player.nickname())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate player nickname in saved match: " + player.nickname()
                );
            }
        }

        if (aggregate.activePlayerNickname() != null && !playerNicknames.contains(aggregate.activePlayerNickname())) {
            throw new SavedMatchSnapshotConsistencyException(
                    "Active player is not part of the saved match: " + aggregate.activePlayerNickname()
            );
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

        Set<Integer> cardIds = new HashSet<>();
        for (SavedMatchBoardCardRecord boardCardRecord : aggregate.boardCards()) {
            if (!cardIds.add(boardCardRecord.cardId())) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Duplicate card id detected across persisted board state: " + boardCardRecord.cardId()
                );
            }
        }

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
        }
    }
}
