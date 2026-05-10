package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerSnapshotMapperTest {
    private final PlayerSnapshotMapper mapper = new PlayerSnapshotMapper();

    @Test
    void toSnapshotCapturesFoodPrestigeAndTribe() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(201, Era.first, TribeCardType.BUILDER, 1, 3));
        tribe.addCharacter(new Gatherer(202, Era.first, TribeCardType.GATHERER));
        PairOfInventor pairBuilding = new PairOfInventor(
                303,
                Era.first,
                4,
                2,
                BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,
                1
        );
        pairBuilding.addInventionTypeAndApplyEffect(InventionType.BOW, new Player("temp", 0, 0, TotemColor.BLACK));
        tribe.addBuilding(pairBuilding);

        Player player = new Player("Alice", 7, 9, TotemColor.BLUE, tribe);

        SavedMatchSnapshot.PlayerSnapshot snapshot = this.mapper.toSnapshot(player);

        assertEquals("Alice", snapshot.nickname());
        assertEquals("BLUE", snapshot.totem());
        assertEquals(7, snapshot.food());
        assertEquals(9, snapshot.prestigePoints());
        assertEquals(List.of(201, 202), snapshot.tribe().characters());
        assertEquals(303, snapshot.tribe().buildings().getFirst().id());
    }

    @Test
    void toSnapshotRejectsNullPlayer() {
        assertThrows(IllegalArgumentException.class, () -> this.mapper.toSnapshot(null));
    }
}
