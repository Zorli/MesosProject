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
    public CardNotPresentException(Card card) {
        super(buildMessage(card));
    }

    /**
     * Builds the diagnostic message associated with the missing-board-card request.
     *
     * @param card requested card reference, possibly {@code null}
     * @return formatted exception message
     */
    private static String buildMessage(Card card) {
        return "Requested card is not present on the board: " + (card == null ? "null" : card.getId());
    }
}
