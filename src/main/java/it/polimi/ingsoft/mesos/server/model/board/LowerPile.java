package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents the lower row of cards on the game board.
 * Manages the storage and lifecycle of tribe cards and building cards
 * that have shifted down from the upper row, as well as the resolution of events.
 */
public class LowerPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    /**
     * Initializes an empty lower pile, ready to receive tribe and building cards.
     */
    public LowerPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    /**
     * Creates a lower pile from already ordered visible cards.
     *
     * @param tribeCards visible tribe cards in row order
     * @param buildingCards visible building cards in row order
     */
    public LowerPile(List<TribeCard> tribeCards, List<BuildingCard> buildingCards) {
        this.tribeCards = new ArrayList<>(tribeCards);
        this.buildingCards = new ArrayList<>(buildingCards);
    }

    /**
     * Smartly adds a single card to the pile, automatically routing it
     * to the correct internal list based on its specific type.
     *
     * @param card The card to be added (TribeCard or BuildingCard).
     */
    public void addCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.add((TribeCard) card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.add((BuildingCard) card);
        }
    }

    /**
     * Adds a collection of tribe cards to the lower row.
     *
     * @param cards The list of tribe cards to add.
     */
    public void addTribeCards(List<TribeCard> cards) {
        this.tribeCards.addAll(cards);
    }

    /**
     * Adds a collection of building cards to the lower row.
     *
     * @param cards The list of building cards to add.
     */
    public void addBuildingCards(List<BuildingCard> cards) {
        this.buildingCards.addAll(cards);
    }

    /**
     * Removes a specific card from the lower row, determining its type
     * to remove it from the correct internal list.
     *
     * @param card The card to remove (typically drafted by a player).
     */
    public void removeCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.remove(card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.remove(card);
        }
    }

    /**
     * Clears all tribe cards currently present in the lower row.
     * Used during the end-of-round shifting phase.
     */
    public void discardLowerRow() {
        this.tribeCards.clear();
    }

    /**
     * Clears all building cards currently present in the lower row.
     * Used during the end-of-era shifting phase.
     */
    public void discardLowerBuildings() {
        this.buildingCards.clear();
    }

    /**
     * Resolves all event cards currently present in the lower row.
     * If multiple regular events of the exact same class are present, they are resolved
     * in chronological order based on their {Era} (oldest to newest).
     * Ensures that any Sustenance Event is always resolved strictly after all
     * regular events, also adhering to the chronological Era order if multiple are present.
     *
     * @param players The list of players affected by the events.
     */
    public void resolveLowerRowEvents(ArrayList<Player> players) {
        List<SustenanceEventCard> sustenanceEvents = new ArrayList<>();
        List<EventCard> regularEvents = new ArrayList<>();

        for (TribeCard card : this.tribeCards) {
            if (card instanceof SustenanceEventCard) {
                sustenanceEvents.add((SustenanceEventCard) card);
            } else if (card instanceof EventCard) {
                regularEvents.add((EventCard) card);
            }
        }
        regularEvents.sort(Comparator.comparing(EventCard::getEra));
        sustenanceEvents.sort(Comparator.comparing(SustenanceEventCard::getEra));
        for (EventCard event : regularEvents) {
            event.applyEffect(players);
        }
        for (SustenanceEventCard event : sustenanceEvents) {
            event.applyEffect(players);
        }
    }

    /**
     * Retrieves the current number of tribe cards in the lower row.
     *
     * @return The size of the tribe cards list.
     */
    public int getTribeCardsSize() {
        return this.tribeCards.size();
    }

    /**
     * Retrieves all cards (both tribe and building cards) currently present in the lower pile.
     *
     * @return A combined list containing all cards in the lower row.
     */
    public List<Card> getAllCards() {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(this.tribeCards);
        allCards.addAll(this.buildingCards);
        return allCards;
    }

    /**
     * Returns the tribe cards currently visible in the lower row preserving their order.
     *
     * @return copy of visible lower tribe cards
     */
    public List<TribeCard> getTribeCards() {
        return new ArrayList<>(this.tribeCards);
    }

    /**
     * Returns the building cards currently visible in the lower row preserving their order.
     *
     * @return copy of visible lower building cards
     */
    public List<BuildingCard> getBuildingCards() {
        return new ArrayList<>(this.buildingCards);
    }
}
