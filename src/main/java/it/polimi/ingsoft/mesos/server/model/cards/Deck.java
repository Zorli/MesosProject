package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.Era;

import java.util.ArrayList;
import java.util.Collections;


public abstract class Deck {
    private ArrayList<Card> cards;

    public Deck(ArrayList<Card> cards){
        this.cards = cards;
        this.shuffle();
    }

    public Card draw(){
        return this.cards.remove(0);
    }
    public boolean isEmpty(){
        return this.cards.isEmpty();
    }

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
