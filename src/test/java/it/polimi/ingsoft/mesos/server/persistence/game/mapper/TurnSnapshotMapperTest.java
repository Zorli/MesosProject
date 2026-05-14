package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnSnapshotMapperTest {

    private final TurnSnapshotMapper mapper = new TurnSnapshotMapper();

    @Test
    void toSnapshotCapturesTurnOrderAndCurrentPhase() {
        Player alice = MesosTestFixtures.createPlayers("Alice").get(0);
        Player bob = MesosTestFixtures.createPlayers("Bob").get(0);

        TurnOrderTrack orderTrack = new TurnOrderTrack(List.of(alice, bob), 1, 5, RoundPhase.PLACING_TOTEMS);
        PlayersTurnFSM playersTurnFSM = new PlayersTurnFSM();
        playersTurnFSM.registerDrawableCards(alice, 2, 1);
        playersTurnFSM.registerDrawableCards(bob, 0, 3);

        SavedMatchSnapshot.TurnStateSnapshot snapshot = mapper.toSnapshot(playersTurnFSM, orderTrack.getTurnOrder());
        SavedMatchSnapshot.TurnOrderSnapshot orderSnapshot = mapper.toSnapshot(orderTrack);

        assertEquals(List.of("Alice", "Bob"), orderSnapshot.nicknames());
        assertEquals(1, orderSnapshot.currentPlayerIndex());
        assertEquals(5, orderSnapshot.roundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS.name(), orderSnapshot.currentRoundPhase());

        assertEquals(TurnPhase.TURN_COMPLETED.name(), snapshot.phase());
        assertEquals(2, snapshot.remainingUpperChoicesByNickname().get("Alice"));
        assertEquals(1, snapshot.remainingLowerChoicesByNickname().get("Alice"));
        assertEquals(0, snapshot.remainingUpperChoicesByNickname().get("Bob"));
        assertEquals(3, snapshot.remainingLowerChoicesByNickname().get("Bob"));
    }

    @Test
    void toSnapshotKeepsExtraPlayersFromTurnStateIfMissingFromOrderTrack() {
        Player alice = MesosTestFixtures.createPlayers("Alice").get(0);
        Player bob = MesosTestFixtures.createPlayers("Bob", "Charlie").get(0);
        Player unknown = MesosTestFixtures.createPlayers("Charlie").get(0);

        PlayersTurnFSM playersTurnFSM = new PlayersTurnFSM();
        playersTurnFSM.registerDrawableCards(alice, 1, 1);
        playersTurnFSM.registerDrawableCards(bob, 1, 1);
        playersTurnFSM.registerDrawableCards(unknown, 2, 2);

        SavedMatchSnapshot.TurnStateSnapshot snapshot = mapper.toSnapshot(playersTurnFSM, List.of(alice));

        assertEquals(Map.of("Alice", 1, "Bob", 1, "Charlie", 2), snapshot.remainingUpperChoicesByNickname());
        assertEquals(1, snapshot.remainingUpperChoicesByNickname().get("Alice"));
        assertEquals(1, snapshot.remainingUpperChoicesByNickname().get("Bob"));
        assertEquals(2, snapshot.remainingUpperChoicesByNickname().get("Charlie"));
    }

    @Test
    void toSnapshotRejectsMissingInput() {
        List<Player> orderedPlayers = MesosTestFixtures.createPlayers("Alice");

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toSnapshot(null, orderedPlayers)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toSnapshot(new PlayersTurnFSM(), null)
        );
    }
}
