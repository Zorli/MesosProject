package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import java.util.ArrayList;
import java.util.List;

public class UpperPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    public UpperPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }
    // getter for setup and refill
    public int getTribeCardsSize() {
        return this.tribeCards.size();
    }
    // --- METODI PER IL REFILL (Usati dalla Board quando pesca dai mazzi) ---
    // in caso di fine round o fine era
    public void addTribeCards(List<TribeCard> cards) {
        this.tribeCards.addAll(cards);
    }
    // Da usare durante il setup o i refill (una carta alla volta)
    public void addCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.add((TribeCard) card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.add((BuildingCard) card);
        }
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
        allCards.addAll(this.tribeCards);
        allCards.addAll(this.buildingCards);

        return allCards;
    }

    // --- METODI PER LO SHIFT (Usati dalla Board a fine round) ---

    /**
     * Estrae tutte le carte Tribù dalla riga superiore, svuotandola,
     * e le restituisce in blocco per farle scorrere giù.
     */
    public List<TribeCard> removeAndReturnAllTribeCards() {
        List<TribeCard> cardsToMove = new ArrayList<>(this.tribeCards);
        this.tribeCards.clear();
        return cardsToMove;
    }

    /**
     * Estrae tutti gli Edifici dalla riga superiore, svuotandola,
     * e li restituisce in blocco per farli scorrere giù.
     */
    public List<BuildingCard> removeAndReturnAllBuildingCards() {
        List<BuildingCard> cardsToMove = new ArrayList<>(this.buildingCards);
        this.buildingCards.clear();
        return cardsToMove;
    }

    // --- METODO PER IL DRAFTING (Usato quando un giocatore pesca una carta) ---

    public void removeCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.remove(card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.remove(card);
        }
    }
}
