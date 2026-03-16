package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.Era;

import java.util.ArrayList;
import java.util.Collections;


/**
 * Abstract base class for decks that can be shuffled and drawn from.
 *
 * <p>The implementation keeps cards grouped by era while shuffling inside each era bucket, so
 * draws preserve the global first-second-third era progression expected by the game rules.
 */
public abstract class Deck {
    private ArrayList<Card> cards;

    /**
     * Creates a deck from the provided card list and shuffles it using the era-aware strategy.
     *
     * @param cards cards to store in the deck
     */
    public Deck(ArrayList<Card> cards){
        this.cards = cards;
        this.shuffle();
    }

    /**
     * Draws and removes the top card of the deck.
     *
     * @return first remaining card in the deck
     */
    public Card draw(){
        return this.cards.remove(0);
    }

    /**
     * Returns whether the deck has no cards left.
     *
     * @return {@code true} when the deck is empty
     */
    public boolean isEmpty(){
        return this.cards.isEmpty();
    }

    /**
     * Shuffles cards inside each era bucket and then rebuilds the deck with eras kept in order.
     */
    public void shuffle(){
        ArrayList<Card> firstEra = new ArrayList<>();
        ArrayList<Card> secondEra = new ArrayList<>();
        ArrayList<Card> thirdEra = new ArrayList<>();

        for (Card card : this.cards) {
            if (card.getEra() == Era.first) {
                firstEra.add(card);
            } else if (card.getEra() == Era.second) {
                secondEra.add(card);
            } else if (card.getEra() == Era.third) {
                thirdEra.add(card);
            }
        }

        Collections.shuffle(firstEra);
        Collections.shuffle(secondEra);
        Collections.shuffle(thirdEra);

        this.cards.clear();
        this.cards.addAll(firstEra);
        this.cards.addAll(secondEra);
        this.cards.addAll(thirdEra);
    }
}
