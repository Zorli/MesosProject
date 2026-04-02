package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardRowSelectionNotAllowedExceptionTest {

    @Test
    void constructorBuildsMessageWithRequestedRow() {
        CardRowSelectionNotAllowedException exception =
                new CardRowSelectionNotAllowedException(CardRow.UPPER);

        assertEquals("Card selection is not allowed from row: UPPER", exception.getMessage());
    }
}
