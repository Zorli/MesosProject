package it.polimi.ingsoft.mesos.server.model.cards;

import java.util.ArrayList;

/**
 * Concrete deck that stores building cards.
 *
 * <p>The deck relies on the base {@link Deck} behavior for era-aware shuffling and sequential
 * draws, while simply constraining the semantic content of the provided card list.
 */
public class BuildingDeck extends Deck{
    /**
     * Creates a building deck from the provided card list.
     *
     * @param cards building cards to store in the deck
     */
    public BuildingDeck(ArrayList<Card> cards) {
            super(cards);
        }
}
