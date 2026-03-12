package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

public class LowerPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    public LowerPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    public void addTribeCards(List<TribeCard> cards) {
        this.tribeCards.addAll(cards);
    }

    public void addBuildingCards(List<BuildingCard> cards) {
        this.buildingCards.addAll(cards);
    }

    /**
     * Restituisce una lista unificata di tutte le carte presenti nella pila
     * (sia Tribù che Edifici). Utile per la Board durante le fasi di pesca.
     */
    public List<Card> getAllCards() {
        List<Card> allCards = new ArrayList<>();

        // Grazie al polimorfismo e ai Generics di Java,
        // possiamo aggiungere tranquillamente entrambe le liste figlie!
        allCards.addAll(this.tribeCards);
        allCards.addAll(this.buildingCards);

        return allCards;
    }

    /**
     * Scarta SOLO le carte Tribù (Personaggi ed Eventi) dalla riga inferiore.
     * I Building rimangono intatti al loro posto.
     */
    public void discardLowerRow() {
        this.tribeCards.clear();
    }

    /**
     * Scarta SOLO gli edifici dalla riga inferiore.
     */
    public void discardLowerBuildings() {
        this.buildingCards.clear();
    }

    /**
     * Risolve gli eventi presenti nella riga inferiore.
     */
    public void resolveLowerRowEvents(List<Player> players, TribeDeck tribeDeck) {
//        for (TribeCard card : this.tribeCards) {
//            if (card instanceof EventCard) {
//                EventCard event = (EventCard) card;
//                // Ipotizzando che la carta evento abbia un metodo resolve()
//                event.resolve(players, tribeDeck);
//            }
//        }
    }

    public void removeCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.remove(card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.remove(card);
        }
    }
}
