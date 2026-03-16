package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;

/**
 * Signals that the player cannot select a card from the requested board row.
 */
public class CardRowSelectionNotAllowedException extends IllegalStateException {

    public CardRowSelectionNotAllowedException(CardRow cardRow) {
        super(buildMessage(cardRow));
    }

    private static String buildMessage(CardRow cardRow) {
        return "Card selection is not allowed from row: " + cardRow;
    }
}
