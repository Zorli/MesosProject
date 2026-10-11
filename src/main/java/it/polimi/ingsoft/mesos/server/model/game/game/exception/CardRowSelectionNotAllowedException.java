package it.polimi.ingsoft.mesos.server.model.game.game.exception;

import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;

/**
 * Signals that the player cannot select a card from the requested board row.
 */
public class CardRowSelectionNotAllowedException extends IllegalStateException {
    /**
     * Creates the exception for a disallowed board-row selection.
     *
     * @param cardRow row the player attempted to draw from
     */
    public CardRowSelectionNotAllowedException(CardRow cardRow, String nickname) {
        super(buildMessage(cardRow,nickname));
    }

    /**
     * Builds the diagnostic message associated with a forbidden row selection.
     *
     * @param cardRow row the player attempted to use
     * @return formatted exception message
     */
    private static String buildMessage(CardRow cardRow, String nickname) {
        return "Card selection for " + nickname + " is not allowed from row: " + cardRow;
    }
}
