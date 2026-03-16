package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionResultTest {

    @Test
    void constructorStoresSnapshotData() {
        Player player = new Player("Alice", 0, 0, TotemColor.RED, null);
        List<Card> upperCards = List.of();
        List<Card> lowerCards = List.of();
        List<Card> drawableUpperCards = List.of();
        List<Card> drawableLowerCards = List.of();

        ActionResult actionResult = new ActionResult(
                TurnPhase.WAITING_FOR_CARD_SELECTION,
                player,
                GameStatus.IN_PROGRESS,
                Era.first,
                3,
                true,
                false,
                false,
                upperCards,
                lowerCards,
                drawableUpperCards,
                drawableLowerCards
        );

        assertEquals(TurnPhase.WAITING_FOR_CARD_SELECTION, actionResult.getTurnPhase());
        assertEquals("Alice", actionResult.getExpectedPlayer());
        assertEquals(GameStatus.IN_PROGRESS, actionResult.getGameStatus());
        assertEquals(Era.first, actionResult.getCurrentEra());
        assertEquals(3, actionResult.getRoundNumber());
        assertTrue(actionResult.isTurnEnded());
        assertFalse(actionResult.isRoundEnded());
        assertFalse(actionResult.isGameEnded());
        assertEquals(upperCards, actionResult.getUpperRowCards());
        assertEquals(lowerCards, actionResult.getLowerRowCards());
        assertEquals(drawableUpperCards, actionResult.getDrawableUpperCards());
        assertEquals(drawableLowerCards, actionResult.getDrawableLowerCards());
    }

}
