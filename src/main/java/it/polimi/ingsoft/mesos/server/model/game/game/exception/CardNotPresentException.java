package it.polimi.ingsoft.mesos.server.model.game.game.exception;

import it.polimi.ingsoft.mesos.server.model.game.cards.Card;

/**
 * Signals that the requested card is not currently present on the board.
 */
public class CardNotPresentException extends IllegalStateException {
    /**
     * Creates the exception for a card that is no longer present on the board.
     *
     * @param card card requested by the player
     */
    public CardNotPresentException(Integer cardId) {
        super(buildMessage(cardId));
    }

    /**
     * Builds the diagnostic message associated with the missing-board-card request.
     *
     * @param card requested card reference, possibly {@code null}
     * @return formatted exception message
     */
    private static String buildMessage(Integer cardId) {
        return "Requested card is not present on the board: " + (cardId == null ? "null" : cardId);
    }
}
