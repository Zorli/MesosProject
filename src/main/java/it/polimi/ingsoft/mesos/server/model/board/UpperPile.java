package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;

import java.util.ArrayList;
import java.util.List;

public class UpperPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public UpperPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }


    // ==========================================
    // ADDING CARDS & REFILL
    // ==========================================

    /**
     * Smart addition: automatically routes the card to the correct internal list.
     * Used during setup and single-card refills.
     */
    public void addCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.add((TribeCard) card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.add((BuildingCard) card);
        }
    }

    public void addTribeCards(List<TribeCard> cards) {
        this.tribeCards.addAll(cards);
    }

    public void addBuildingCards(List<BuildingCard> cards) {
        this.buildingCards.addAll(cards);
    }


    // ==========================================
    // SHIFTING LOGIC (END OF ROUND/ERA)
    // ==========================================

    /**
     * Extracts all Tribe cards from the upper row, clears it,
     * and returns them as a block to shift them down.
     */
    public List<TribeCard> removeAndReturnAllTribeCards() {
        List<TribeCard> cardsToMove = new ArrayList<>(this.tribeCards);
        this.tribeCards.clear();
        return cardsToMove;
    }

    /**
     * Extracts all Building cards from the upper row, clears it,
     * and returns them as a block to shift them down.
     */
    public List<BuildingCard> removeAndReturnAllBuildingCards() {
        List<BuildingCard> cardsToMove = new ArrayList<>(this.buildingCards);
        this.buildingCards.clear();
        return cardsToMove;
    }


    // ==========================================
    // REMOVING CARDS (DRAFTING)
    // ==========================================

    /**
     * Removes a specific card after it has been drafted/purchased by a player.
     */
    public void removeCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.remove(card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.remove(card);
        }
    }


    // ==========================================
    // GETTERS & QUERIES
    // ==========================================

    public int getTribeCardsSize() {
        return this.tribeCards.size();
    }

    /**
     * Returns a unified list of all cards present in the pile
     * (both Tribe and Buildings). Useful for drafting phases.
     */
    public List<Card> getAllCards() {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(this.tribeCards);
        allCards.addAll(this.buildingCards);
        return allCards;
    }
}