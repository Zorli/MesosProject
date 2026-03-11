package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.Era;

import java.util.ArrayList;
import java.util.Collections;


public abstract class Deck {
    private ArrayList<TribeCard> cards;

    public Deck(ArrayList<TribeCard> cards){
        this.cards = cards;
        this.shuffle();
    }

    public TribeCard draw(){
        return this.cards.remove(0);
    }
    public boolean isEmpty(){
        return this.cards.isEmpty();
    }

    public void shuffle(){
        ArrayList<TribeCard> firstEra = new ArrayList<>();
        ArrayList<TribeCard> secondEra = new ArrayList<>();
        ArrayList<TribeCard> thirdEra = new ArrayList<>();

        for (TribeCard card : this.cards) {
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
