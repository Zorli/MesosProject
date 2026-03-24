package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import it.polimi.ingsoft.mesos.server.controller.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnStateTest {

    @Test
    void registerDrawableCardsRejectsNullPlayerAndNegativeChoices() {
        TurnState turnState = new TurnState();
        Player alice = player("Alice", TotemColor.RED);

        assertThrows(IllegalArgumentException.class, () -> turnState.registerDrawableCards(null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> turnState.registerDrawableCards(alice, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> turnState.registerDrawableCards(alice, 0, -1));
    }

    @Test
    void startCardSelectionRequiresRegisteredPlayerAndTransitionsPhase() {
        TurnState turnState = new TurnState();
        Player alice = player("Alice", TotemColor.RED);

        assertThrows(IllegalArgumentException.class, () -> turnState.startCardSelection(null));
        assertThrows(IllegalStateException.class, () -> turnState.startCardSelection(alice));

        turnState.registerDrawableCards(alice, 1, 2);
        turnState.startCardSelection(alice);

        assertEquals(TurnPhase.WAITING_FOR_CARD_SELECTION, turnState.getPhase());
        assertTrue(turnState.canChooseFromUpperRow(alice));
        assertTrue(turnState.canChooseFromLowerRow(alice));
        assertTrue(turnState.hasDrawableCards(alice));
    }

    @Test
    void onCardChosenConsumesChoicesAcrossRowsAndCompletesTurn() {
        TurnState turnState = new TurnState();
        Player alice = player("Alice", TotemColor.RED);
        turnState.registerDrawableCards(alice, 1, 1);
        turnState.startCardSelection(alice);

        turnState.onCardChosen(alice, CardRow.UPPER);
        assertEquals(0, turnState.getRemainingUpperChoices(alice));
        assertEquals(1, turnState.getRemainingLowerChoices(alice));
        assertEquals(TurnPhase.WAITING_FOR_CARD_SELECTION, turnState.getPhase());

        turnState.onCardChosen(alice, CardRow.LOWER);
        assertEquals(0, turnState.getRemainingLowerChoices(alice));
        assertEquals(TurnPhase.TURN_COMPLETED, turnState.getPhase());
        assertFalse(turnState.hasDrawableCards(alice));
        assertTrue(turnState.isTurnCompleted());
    }

    @Test
    void onCardChosenRejectsUnavailableRowsAndMissingPlayer() {
        TurnState turnState = new TurnState();
        Player alice = player("Alice", TotemColor.RED);
        turnState.registerDrawableCards(alice, 0, 1);
        turnState.startCardSelection(alice);

        assertThrows(IllegalArgumentException.class, () -> turnState.onCardChosen(null, CardRow.UPPER));
        assertThrows(IllegalStateException.class, () -> turnState.onCardChosen(alice, CardRow.UPPER));
        assertThrows(IllegalArgumentException.class, () -> turnState.onCardChosen(alice, CardRow.NOT_PRESENT));
    }

    @Test
    void resetMethodsClearTrackedChoices() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TurnState turnState = new TurnState(
                TurnPhase.WAITING_FOR_CARD_SELECTION,
                Map.of(alice, 1, bob, 2),
                Map.of(alice, 2, bob, 1)
        );

        turnState.resetForNextPlayer(alice);
        assertEquals(0, turnState.getRemainingUpperChoices(alice));
        assertEquals(0, turnState.getRemainingLowerChoices(alice));
        assertEquals(2, turnState.getRemainingUpperChoices(bob));
        assertEquals(TurnPhase.TURN_COMPLETED, turnState.getPhase());

        turnState.resetForNewRound();
        assertTrue(turnState.getRemainingUpperChoicesByPlayer().isEmpty());
        assertTrue(turnState.getRemainingLowerChoicesByPlayer().isEmpty());
        assertFalse(turnState.isWaitingForCardSelection());
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color, new Tribe());
    }
}
