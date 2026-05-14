package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps turn-related game state into persistence-ready snapshots.
 */
public final class TurnSnapshotMapper {
    public SavedMatchSnapshot.TurnOrderSnapshot toSnapshot(TurnOrderTrack turnOrderTrack) {
        if (turnOrderTrack == null) {
            throw new IllegalArgumentException("turnOrderTrack cannot be null");
        }

        return new SavedMatchSnapshot.TurnOrderSnapshot(
                turnOrderTrack.getTurnOrder()
                        .stream()
                        .map(Player::getNickname)
                        .toList(),
                turnOrderTrack.getCurrentPlayerIndex(),
                turnOrderTrack.getRoundNumber(),
                turnOrderTrack.getCurrentRoundPhase().name()
        );
    }

    public SavedMatchSnapshot.TurnStateSnapshot toSnapshot(PlayersTurnFSM playersTurnFSM, List<Player> orderedPlayers) {
        if (playersTurnFSM == null) {
            throw new IllegalArgumentException("playersTurnFSM cannot be null");
        }
        if (orderedPlayers == null) {
            throw new IllegalArgumentException("orderedPlayers cannot be null");
        }

        return new SavedMatchSnapshot.TurnStateSnapshot(
                playersTurnFSM.getPhase().name(),
                mapChoicesByNickname(orderedPlayers, playersTurnFSM.getRemainingUpperChoicesByPlayer()),
                mapChoicesByNickname(orderedPlayers, playersTurnFSM.getRemainingLowerChoicesByPlayer())
        );
    }

    private Map<String, Integer> mapChoicesByNickname(
            List<Player> orderedPlayers,
            Map<Player, Integer> sourceMap
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (sourceMap == null || sourceMap.isEmpty()) {
            return result;
        }
        for (Player player : orderedPlayers) {
            Integer choiceCount = sourceMap.get(player);
            if (choiceCount != null) {
                result.put(player.getNickname(), choiceCount);
            }
        }
        // Include any entries present in sourceMap but absent from orderedPlayers
        sourceMap.forEach((player, choiceCount) -> {
            if (player != null && player.getNickname() != null) {
                result.putIfAbsent(player.getNickname(), choiceCount);
            }
        });
        return result;
    }
}
