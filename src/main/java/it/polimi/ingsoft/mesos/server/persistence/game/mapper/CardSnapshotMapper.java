package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedCardSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Maps persisted card state to and from JSON-friendly snapshots.
 */
public final class CardSnapshotMapper {

    /**
     * Creates a compact card snapshot for persistence.
     *
     * @param card card instance owned by game model
     * @return serializable snapshot
     */
    public SavedCardSnapshot toSnapshot(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        if (card instanceof BuildingCard buildingCard) {
            Map<String, Integer> runtimeState = buildingCard.exportRuntimeState();
            return new SavedCardSnapshot(buildingCard.getId(), runtimeState);
        }
        return new SavedCardSnapshot(card.getId(), Map.of());
    }

    /**
     * Creates persisted snapshots for all provided building cards.
     *
     * @param cards buildings to snapshot
     * @return serialized building snapshot list
     */
    public List<SavedCardSnapshot> toBuildingSnapshots(List<BuildingCard> cards) {
        return cards.stream()
                .map(this::toSnapshot)
                .toList();
    }

    public SavedMatchSnapshot.TribeSnapshot toTribeSnapshot(
            List<Integer> characterIds,
            List<SavedCardSnapshot> buildingSnapshots
    ) {
        return new SavedMatchSnapshot.TribeSnapshot(
                List.copyOf(characterIds),
                List.copyOf(buildingSnapshots)
        );
    }
}
