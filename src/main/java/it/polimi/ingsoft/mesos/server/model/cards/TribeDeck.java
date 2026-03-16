package it.polimi.ingsoft.mesos.server.model.cards;

import java.util.ArrayList;

/**
 * Concrete deck that stores tribe cards, including both characters and events.
 */
public class TribeDeck extends Deck {
    /**
     * Creates a tribe deck from the provided card list.
     *
     * @param cards tribe cards to store in the deck
     */
    public TribeDeck(ArrayList<Card> cards) {
        super(cards);
    }
}
