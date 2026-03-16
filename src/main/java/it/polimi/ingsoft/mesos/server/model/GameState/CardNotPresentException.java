package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.Card;

/**
 * Signals that the requested card is not currently present on the board.
 */
public class CardNotPresentException extends IllegalStateException {

    public CardNotPresentException(Card card) {
        super(buildMessage(card));
    }

    private static String buildMessage(Card card) {
        return "Requested card is not present on the board: " + (card == null ? "null" : card.getId());
    }
}
