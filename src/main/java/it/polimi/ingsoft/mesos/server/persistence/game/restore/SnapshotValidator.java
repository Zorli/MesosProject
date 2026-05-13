package it.polimi.ingsoft.mesos.server.persistence.game.restore;

import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates snapshot invariants required for safe game restoration.
 */
public final class SnapshotValidator {
    private static final int EXPECTED_SCHEMA_VERSION = 1;

    /**
     * Validates structural constraints on a persisted match snapshot.
     *
     * @param snapshot snapshot to validate
     */
    public void validate(SavedMatchSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }

        if (snapshot.schemaVersion() != EXPECTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema version: " + snapshot.schemaVersion());
        }

        if (snapshot.game() == null || snapshot.game().hostNickname() == null || snapshot.game().hostNickname().isBlank()) {
            throw new IllegalArgumentException("game.hostNickname cannot be null or blank");
        }

        if (snapshot.game().id() <= 0) {
            throw new IllegalArgumentException("game.id must be positive");
        }

        if (snapshot.game().status() == null || snapshot.game().status().isBlank()) {
            throw new IllegalArgumentException("game.status cannot be null or blank");
        }

        if (snapshot.game().currentEra() == null || snapshot.game().currentEra().isBlank()) {
            throw new IllegalArgumentException("game.currentEra cannot be null or blank");
        }

        if (snapshot.players() == null || snapshot.players().isEmpty()) {
            throw new IllegalArgumentException("players cannot be null or empty");
        }

        if (snapshot.board() == null) {
            throw new IllegalArgumentException("board cannot be null");
        }
        if (snapshot.board().offerSpaces() == null || snapshot.board().startingBlocks() == null) {
            throw new IllegalArgumentException("board offerSpaces/startingBlocks cannot be null");
        }

        if (snapshot.turnOrder() == null) {
            throw new IllegalArgumentException("turnOrder cannot be null");
        }

        if (snapshot.turnOrder().nicknames() == null || snapshot.turnOrder().nicknames().isEmpty()) {
            throw new IllegalArgumentException("turnOrder.nicknames cannot be null or empty");
        }

        if (snapshot.turnState() == null) {
            throw new IllegalArgumentException("turnState cannot be null");
        }
        if (snapshot.turnState().phase() == null || snapshot.turnState().phase().isBlank()) {
            throw new IllegalArgumentException("turnState.phase cannot be null or blank");
        }

        validateTurnStateMap(snapshot.turnState().remainingUpperChoicesByNickname());
        validateTurnStateMap(snapshot.turnState().remainingLowerChoicesByNickname());

        Set<String> playerNicknames = playerNicknames(snapshot);
        if (!playerNicknames.contains(snapshot.game().hostNickname())) {
            throw new IllegalArgumentException("Host nickname not part of restored players: " + snapshot.game().hostNickname());
        }

        for (String playerNickname : snapshot.turnOrder().nicknames()) {
            if (playerNickname == null || playerNickname.isBlank()) {
                throw new IllegalArgumentException("turnOrder.nicknames must not contain blank names");
            }
            if (!playerNicknames.contains(playerNickname)) {
                throw new IllegalArgumentException("turnOrder contains unknown nickname: " + playerNickname);
            }
        }

        if (snapshot.game().stateVersion() < 0) {
            throw new IllegalArgumentException("game.stateVersion cannot be negative");
        }

        validateBoardLists(snapshot.board());

        this.validateTurnStateReferences(snapshot, playerNicknames);
    }

    private void validateBoardLists(SavedMatchSnapshot.BoardSnapshot board) {
        if (board.remainingTribeDeck() == null
                || board.remainingBuildingDeck() == null
                || board.upperTribeCards() == null
                || board.upperBuildingCards() == null
                || board.lowerTribeCards() == null
                || board.lowerBuildingCards() == null) {
            throw new IllegalArgumentException("board card lists cannot be null");
        }
        for (SavedMatchSnapshot.OfferSpaceSnapshot offerSpace : board.offerSpaces()) {
            if (offerSpace == null) {
                throw new IllegalArgumentException("offerSpaces cannot contain null entries");
            }
            if (offerSpace.code() == null || offerSpace.code().isBlank()) {
                throw new IllegalArgumentException("offerSpaces code cannot be null or blank");
            }
        }
        for (SavedMatchSnapshot.StartingBlockSnapshot startingBlock : board.startingBlocks()) {
            if (startingBlock == null) {
                throw new IllegalArgumentException("startingBlocks cannot contain null entries");
            }
        }
    }

    /**
     * Validates turn-state reference maps point only to known players.
     *
     * @param snapshot source snapshot
     * @param playerNicknames player nicknames restored from the snapshot
     */
    public void validateTurnStateReferences(SavedMatchSnapshot snapshot, Set<String> playerNicknames) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }

        if (playerNicknames == null || playerNicknames.isEmpty()) {
            throw new IllegalArgumentException("playerNicknames cannot be null or empty");
        }

        validateTurnStateReferences(snapshot.turnState().remainingUpperChoicesByNickname(), playerNicknames);
        validateTurnStateReferences(snapshot.turnState().remainingLowerChoicesByNickname(), playerNicknames);
    }

    private void validateTurnStateReferences(
            Map<String, Integer> turnStateByNickname,
            Set<String> playerNicknames
    ) {
        if (turnStateByNickname == null) {
            return;
        }

        for (Map.Entry<String, Integer> entry : turnStateByNickname.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("turnState references cannot contain blank nicknames");
            }
            if (!playerNicknames.contains(entry.getKey())) {
                throw new IllegalArgumentException("turnState references unknown nickname: " + entry.getKey());
            }
        }
    }

    private Set<String> playerNicknames(SavedMatchSnapshot snapshot) {
        Set<String> playerNicknames = new HashSet<>();
        for (SavedMatchSnapshot.PlayerSnapshot player : snapshot.players()) {
            if (player == null || player.nickname() == null || player.nickname().isBlank()) {
                throw new IllegalArgumentException("player snapshot must include a valid nickname");
            }
            if (!playerNicknames.add(player.nickname())) {
                throw new IllegalArgumentException("duplicate player nickname in snapshot: " + player.nickname());
            }

            if (player.tribe() == null) {
                throw new IllegalArgumentException("player.tribe cannot be null");
            }
        }
        return playerNicknames;
    }

    private void validateTurnStateMap(Map<String, Integer> turnChoicesByNickname) {
        if (turnChoicesByNickname == null) {
            return;
        }

        for (Map.Entry<String, Integer> entry : turnChoicesByNickname.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("turnState references cannot contain blank nicknames");
            }
            Integer value = entry.getValue();
            if (value == null || value < 0) {
                throw new IllegalArgumentException("turnState values must be non-negative");
            }
        }
    }
}
