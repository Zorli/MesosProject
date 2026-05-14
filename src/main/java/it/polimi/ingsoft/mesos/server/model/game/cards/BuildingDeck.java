package it.polimi.ingsoft.mesos.server.model.game.cards;

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

    /**
     * Creates a building deck preserving the provided card order.
     *
     * @param orderedCards building cards already ordered as the remaining draw pile
     * @return deck preserving the supplied order
     */
    public static BuildingDeck fromOrderedCards(ArrayList<Card> orderedCards) {
        return new BuildingDeck(orderedCards, false);
    }

    /**
     * Creates a building deck optionally preserving the supplied order.
     *
     * @param cards cards that make up the draw pile
     * @param shuffleOnCreate whether the deck should be shuffled during construction
     */
    private BuildingDeck(ArrayList<Card> cards, boolean shuffleOnCreate) {
        super(cards, shuffleOnCreate);
    }
}
