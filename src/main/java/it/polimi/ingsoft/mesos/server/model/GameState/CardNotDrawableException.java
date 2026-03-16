package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Signals that a card is present on the board but cannot currently be drawn by the player.
 */
public class CardNotDrawableException extends IllegalStateException {

    public CardNotDrawableException(Player player, Card card) {
        super(buildMessage(player, card));
    }

    private static String buildMessage(Player player, Card card) {
        String playerNickname = player == null ? "unknown" : player.getNickname();
        String cardId = card == null ? "null" : String.valueOf(card.getId());
        return "Card " + cardId + " is not drawable by player " + playerNickname;
    }
}
