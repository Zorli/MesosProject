package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.RoundFlow;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RoundFlow}.
 *
 * <p>The class is only a round cursor: it stores the active phase, the round
 * number, the current phase order, and the current player index. These tests
 * verify that small contract directly, without involving {@code Game},
 * {@code Board}, or any card rules.
 */
class RoundFlowTest {

    /**
     * A new flow has no player order yet because the real order is provided by
     * game setup. It still exposes the default round number and default phase.
     */
    @Test
    void newRoundFlowStartsWithoutInitializedOrder() {
        RoundFlow flow = new RoundFlow();

        assertEquals(0, flow.getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, flow.getCurrentRoundPhase());
        assertThrows(IllegalStateException.class, flow::getCurrentPlayer);
        assertThrows(IllegalStateException.class, flow::getCurrentPhaseOrder);
        assertThrows(IllegalStateException.class, flow::getCurrentPlayerIndex);
        assertThrows(IllegalStateException.class, flow::isLastPlayer);
    }

    /**
     * Setting a phase order makes the first player current and keeps the same
     * order visible to callers.
     */
    @Test
    void settingPhaseOrderSelectsTheFirstPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        RoundFlow flow = new RoundFlow();

        flow.setCurrentPhaseOrder(List.of(alice, bob));

        assertEquals(alice, flow.getCurrentPlayer());
        assertEquals(0, flow.getCurrentPlayerIndex());
        assertEquals(List.of(alice, bob), flow.getCurrentPhaseOrder());
    }

    /**
     * The order stored inside the flow must not be tied to the mutable list
     * passed by the caller. Otherwise external code could alter the turn order
     * without using RoundFlow's API.
     */
    @Test
    void settingPhaseOrderCopiesTheInputList() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        List<Player> originalOrder = new ArrayList<>(List.of(alice, bob));
        RoundFlow flow = new RoundFlow();

        flow.setCurrentPhaseOrder(originalOrder);
        originalOrder.clear();

        assertEquals(List.of(alice, bob), flow.getCurrentPhaseOrder());
    }

    /**
     * The returned order is also a copy. Mutating it must not change the
     * current order or the current player stored by the flow.
     */
    @Test
    void returnedPhaseOrderCanBeModifiedWithoutChangingTheFlow() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        RoundFlow flow = new RoundFlow();
        flow.setCurrentPhaseOrder(List.of(alice, bob));

        List<Player> returnedOrder = flow.getCurrentPhaseOrder();
        returnedOrder.clear();

        assertEquals(List.of(alice, bob), flow.getCurrentPhaseOrder());
        assertEquals(alice, flow.getCurrentPlayer());
    }

    /**
     * Advancing moves the cursor one player at a time. Reaching the last player
     * is allowed; advancing beyond that point is rejected.
     */
    @Test
    void advancingMovesToNextPlayerAndRejectsAdvancingPastTheEnd() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        RoundFlow flow = new RoundFlow();
        flow.setCurrentPhaseOrder(List.of(alice, bob));

        assertTrue(!flow.isLastPlayer());

        flow.advanceToNextPlayer();

        assertEquals(bob, flow.getCurrentPlayer());
        assertEquals(1, flow.getCurrentPlayerIndex());
        assertTrue(flow.isLastPlayer());
        assertThrows(IllegalStateException.class, flow::advanceToNextPlayer);
    }

    /**
     * Replacing the order starts a new phase order from its first player, even
     * if the previous order had already advanced.
     */
    @Test
    void replacingPhaseOrderResetsTheCurrentPlayerIndex() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);
        RoundFlow flow = new RoundFlow();
        flow.setCurrentPhaseOrder(List.of(alice, bob));
        flow.advanceToNextPlayer();

        flow.setCurrentPhaseOrder(List.of(carol, alice));

        assertEquals(carol, flow.getCurrentPlayer());
        assertEquals(0, flow.getCurrentPlayerIndex());
        assertEquals(List.of(carol, alice), flow.getCurrentPhaseOrder());
    }

    /**
     * A phase order must contain at least one player. Null and empty lists are
     * both invalid because the rest of the API assumes a current player exists.
     */
    @Test
    void phaseOrderCannotBeNullOrEmpty() {
        RoundFlow flow = new RoundFlow();

        assertThrows(IllegalArgumentException.class, () -> flow.setCurrentPhaseOrder(null));
        assertThrows(IllegalArgumentException.class, () -> flow.setCurrentPhaseOrder(List.of()));
    }

    /**
     * Round number and phase are independent pieces of state. The flow does not
     * enforce game-rule transitions; it only stores the values requested by the
     * game flow handlers.
     */
    @Test
    void roundNumberAndPhaseCanBeAdvancedIndependently() {
        RoundFlow flow = new RoundFlow();

        flow.advanceRoundNumber();
        flow.advanceRoundNumber();
        flow.setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);

        assertEquals(2, flow.getRoundNumber());
        assertEquals(RoundPhase.PLAYER_DRAWS, flow.getCurrentRoundPhase());
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }
}
