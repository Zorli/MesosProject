package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedCardSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.List;

/**
 * Maps players and their tribe state to persistence snapshots.
 */
public final class PlayerSnapshotMapper {
    private final CardSnapshotMapper cardSnapshotMapper;

    public PlayerSnapshotMapper() {
        this(new CardSnapshotMapper());
    }

    public PlayerSnapshotMapper(CardSnapshotMapper cardSnapshotMapper) {
        this.cardSnapshotMapper = cardSnapshotMapper;
    }

    public SavedMatchSnapshot.PlayerSnapshot toSnapshot(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        return new SavedMatchSnapshot.PlayerSnapshot(
                player.getNickname(),
                player.getTotem().name(),
                player.getFood(),
                player.getPrestigePoints(),
                toSnapshot(player.getTribe())
        );
    }

    private SavedMatchSnapshot.TribeSnapshot toSnapshot(Tribe tribe) {
        List<Integer> characterIds = tribe.getCharacters()
                .stream()
                .map(CharacterCard::getId)
                .toList();

        List<BuildingCard> buildings = tribe.getBuildings();
        List<SavedCardSnapshot> buildingSnapshots = this.cardSnapshotMapper.toBuildingSnapshots(buildings);

        return this.cardSnapshotMapper.toTribeSnapshot(characterIds, buildingSnapshots);
    }
}
