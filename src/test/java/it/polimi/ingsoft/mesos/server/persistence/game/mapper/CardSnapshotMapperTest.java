package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedCardSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardSnapshotMapperTest {
    private static final String ALICE = "Alice";

    private final CardSnapshotMapper mapper = new CardSnapshotMapper();

    @Test
    void toSnapshotCapturesBuildingRuntimeState() {
        PairOfInventor pairOfInventor = new PairOfInventor(
                401,
                Era.first,
                4,
                2,
                BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,
                3
        );
        pairOfInventor.addInventionTypeAndApplyEffect(InventionType.BOW, new Player(ALICE, 0, 0, TotemColor.RED));

        SavedCardSnapshot snapshot = this.mapper.toSnapshot(pairOfInventor);

        assertEquals(401, snapshot.id());
        assertEquals(1, snapshot.runtimeState().get(InventionType.BOW.name()));
    }

    @Test
    void toSnapshotReturnsEmptyStateForCharacterCards() {
        SavedCardSnapshot snapshot = this.mapper.toSnapshot(new Gatherer(502, Era.first, TribeCardType.GATHERER));

        assertEquals(502, snapshot.id());
        assertEquals(Map.of(), snapshot.runtimeState());
    }

    @Test
    void toBuildingSnapshotsPreservesOrderAndRuntimeStateForEachBuilding() {
        PairOfInventor pairBuilding = new PairOfInventor(
                303,
                Era.first,
                4,
                2,
                BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,
                1
        );
        pairBuilding.addInventionTypeAndApplyEffect(InventionType.WHEEL, new Player(ALICE, 0, 0, TotemColor.BLUE));
        BuildingCard plainBuilding = new BuildingCard(304, Era.second, 5, 0, BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET);

        List<SavedCardSnapshot> snapshots = this.mapper.toBuildingSnapshots(List.of(pairBuilding, plainBuilding));

        assertEquals(2, snapshots.size());
        assertEquals(303, snapshots.get(0).id());
        assertEquals(1, snapshots.get(0).runtimeState().get(InventionType.WHEEL.name()));
        assertEquals(304, snapshots.get(1).id());
        assertEquals(Map.of(), snapshots.get(1).runtimeState());
    }
}
