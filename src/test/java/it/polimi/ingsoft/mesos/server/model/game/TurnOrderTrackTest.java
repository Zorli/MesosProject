package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnOrderTrackTest {

    @Test
    void defaultTrackerRequiresOrderInitializationBeforeAccess() {
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();

        assertThrows(IllegalStateException.class, turnOrderTrack::getCurrentPlayer);
        assertThrows(IllegalStateException.class, turnOrderTrack::getTurnOrder);
        assertThrows(IllegalStateException.class, turnOrderTrack::getCurrentPlayerIndex);
        assertThrows(IllegalStateException.class, turnOrderTrack::advanceToNextPlayer);
        assertThrows(IllegalStateException.class, turnOrderTrack::isLastPlayer);
    }

    @Test
    void restoredConstructorRejectsInvalidOrderOrIndex() {
        Player alice = player("Alice", TotemColor.RED);

        assertThrows(IllegalArgumentException.class, () -> new TurnOrderTrack(null, 0, 1, RoundPhase.PLACING_TOTEMS));
        assertThrows(IllegalArgumentException.class, () -> new TurnOrderTrack(List.of(), 0, 1, RoundPhase.PLACING_TOTEMS));
        assertThrows(IllegalArgumentException.class, () -> new TurnOrderTrack(List.of(alice), -1, 1, RoundPhase.PLACING_TOTEMS));
        assertThrows(IllegalArgumentException.class, () -> new TurnOrderTrack(List.of(alice), 1, 1, RoundPhase.PLACING_TOTEMS));
    }

    @Test
    void setNewOrderResetsCurrentPlayerAndReturnsDefensiveCopy() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice, bob), 1, 2, RoundPhase.PLAYER_DRAWS);

        turnOrderTrack.setNewOrder(List.of(bob, alice));
        List<Player> snapshot = turnOrderTrack.getTurnOrder();

        assertEquals("Bob", turnOrderTrack.getCurrentPlayer().getNickname());
        assertEquals(0, turnOrderTrack.getCurrentPlayerIndex());
        assertNotSame(snapshot, turnOrderTrack.getTurnOrder());
        snapshot.clear();
        assertEquals(2, turnOrderTrack.getTurnOrder().size());
    }

    @Test
    void advanceToNextPlayerTracksLastPlayerAndRejectsOverflow() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.PLACING_TOTEMS);

        assertEquals("Alice", turnOrderTrack.getCurrentPlayer().getNickname());
        assertTrue(!turnOrderTrack.isLastPlayer());

        turnOrderTrack.advanceToNextPlayer();

        assertEquals("Bob", turnOrderTrack.getCurrentPlayer().getNickname());
        assertTrue(turnOrderTrack.isLastPlayer());
        assertThrows(IllegalStateException.class, turnOrderTrack::advanceToNextPlayer);
    }

    @Test
    void roundNumberAndPhaseCanBeUpdated() {
        Player alice = player("Alice", TotemColor.RED);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 0, RoundPhase.PLACING_TOTEMS);

        turnOrderTrack.advanceRoundNumber();
        turnOrderTrack.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);

        assertEquals(1, turnOrderTrack.getRoundNumber());
        assertEquals(RoundPhase.RESOLVING_EFFECTS, turnOrderTrack.getCurrentRoundPhase());
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color, new Tribe());
    }
}
