package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnOrderTrackTest {
    //Test if I get correctly the round number and the current player and the controller on last player
    @Test
    void setNewOrderStartsRoundFromFirstPlayer() {
        TurnOrderTrack track = new TurnOrderTrack();
        Player alice = playerWithNickname("Alice");
        Player bob = playerWithNickname("Bob");
        List<Player>  players = new ArrayList<Player>();
        players.add(alice);
        players.add(bob);
        track.setNewOrder(players);

        assertEquals(1, track.getRoundNumber());
        assertEquals(alice, track.getCurrentPlayer());
        assertFalse(track.isLastPlayer());
    }

    @Test
    void advanceToNextPlayerMovesToFollowingPlayer() {
        TurnOrderTrack track = new TurnOrderTrack();
        Player alice = playerWithNickname("Alice");
        Player bob = playerWithNickname("Bob");
        List<Player>  players = new ArrayList<Player>();
        players.add(alice);
        players.add(bob);
        track.setNewOrder(players);
        track.advanceToNextPlayer();

        assertEquals(bob, track.getCurrentPlayer());
        assertTrue(track.isLastPlayer());
    }

    @Test
    void advanceToNextPlayerThrowsWhenAlreadyOnLastPlayer() {
        TurnOrderTrack track = new TurnOrderTrack();
        track.setNewOrder(List.of(playerWithNickname("Alice")));
        assertThrows(IllegalStateException.class, track::advanceToNextPlayer);
    }

    @Test
    void stateAccessThrowsBeforeOrderInitialization() {
        TurnOrderTrack track = new TurnOrderTrack();

        assertThrows(IllegalStateException.class, track::getCurrentPlayer);
        assertThrows(IllegalStateException.class, track::isLastPlayer);
    }

    //Test on passing empty or null list
    @Test
    void setNewOrderThrowsWhenListIsNullOrEmpty() {
        TurnOrderTrack track = new TurnOrderTrack();

        assertThrows(IllegalArgumentException.class, () -> track.setNewOrder(null));
        assertThrows(IllegalArgumentException.class, () -> track.setNewOrder(new ArrayList<Player>()));
    }

    @Test
    void setNewOrderCopiesInputListDefensively() {
        TurnOrderTrack track = new TurnOrderTrack();
        Player alice = playerWithNickname("Alice");
        Player bob = playerWithNickname("Bob");
        Player carol = playerWithNickname("Carol");
        List<Player> mutableOrder = new ArrayList<>(List.of(alice, bob));

        track.setNewOrder(mutableOrder);
        mutableOrder.clear();
        mutableOrder.add(carol);

        assertEquals(alice, track.getCurrentPlayer());
        track.advanceToNextPlayer();
        assertEquals(bob, track.getCurrentPlayer());
    }

    private Player playerWithNickname(String nickname) {
        return new Player(nickname, 0, 0, TotemColor.RED, null);
    }
}
