package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

public class LowerPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public LowerPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }


    // ==========================================
    // ADDING CARDS
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
    // REMOVING & DISCARDING CARDS
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

    /**
     * Discards ONLY the Tribe cards (Characters and Events) from the lower row.
     * Buildings remain intact in their position.
     */
    public void discardLowerRow() {
        this.tribeCards.clear();
    }

    /**
     * Discards ONLY the Building cards from the lower row.
     */
    public void discardLowerBuildings() {
        this.buildingCards.clear();
    }


    // ==========================================
    // EVENT RESOLUTION
    // ==========================================

    /**
     * Resolves all events present in the lower row.
     * The Sustenance Event is always resolved last according to game rules.
     */
    public void resolveLowerRowEvents(ArrayList<Player> players) {
        SustenanceEventCard sustenanceEvent = null;

        for (TribeCard card : this.tribeCards) {
            if (card instanceof SustenanceEventCard) {
                sustenanceEvent = (SustenanceEventCard) card;
            } else if (card instanceof EventCard) {
                ((EventCard) card).applyEffect(players);
            }
        }

        if (sustenanceEvent != null) {
            sustenanceEvent.applyEffect(players);
        }
    }


    // ==========================================
    // GETTERS & QUERIES
    // ==========================================

    public int getTribeCardsSize() {
        return this.tribeCards.size();
    }

    /**
     * Returns a unified list of all cards present in the pile (both Tribe and Buildings).
     * Useful for the Board during the drafting phases.
     */
    public List<Card> getAllCards() {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(this.tribeCards);
        allCards.addAll(this.buildingCards);
        return allCards;
    }
}