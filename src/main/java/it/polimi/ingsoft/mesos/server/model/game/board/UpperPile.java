package it.polimi.ingsoft.mesos.server.model.game.board;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the upper row of cards on the game board.
 * Manages the storage, addition, and shifting of tribe cards and building cards
 * before they are moved to the lower pile or drafted by players.
 */
public class UpperPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    /**
     * Initializes an empty upper pile, ready to receive tribe and building cards
     * during the setup or refill phases.
     */
    public UpperPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    /**
     * Smartly adds a single card to the pile, automatically routing it
     * to the correct internal list based on its specific type.
     * Used during initial setup and single-card refills.
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
     * Extracts all tribe cards currently present in the upper row and clears the internal list.
     * This is primarily used during the end-of-round shifting phase to move cards to the lower row.
     *
     * @return A list containing all the tribe cards that were in the upper row.
     */
    public List<TribeCard> removeAndReturnAllTribeCards() {
        List<TribeCard> cardsToMove = new ArrayList<>(this.tribeCards);
        this.tribeCards.clear();
        return cardsToMove;
    }

    /**
     * Extracts all building cards currently present in the upper row and clears the internal list.
     * This is primarily used during the end-of-era shifting phase to move buildings to the lower row.
     *
     * @return A list containing all the building cards that were in the upper row.
     */
    public List<BuildingCard> removeAndReturnAllBuildingCards() {
        List<BuildingCard> cardsToMove = new ArrayList<>(this.buildingCards);
        this.buildingCards.clear();
        return cardsToMove;
    }

    /**
     * Removes a specific card from the upper row, determining its type
     * to remove it from the correct internal list.
     * Used when a player successfully drafts a card directly from the upper row.
     *
     * @param card The drafted card to remove.
     */
    public void removeCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.remove(card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.remove(card);
        }
    }

    /**
     * Retrieves the current number of tribe cards in the upper row.
     * Useful for calculating how many cards are needed during a refill.
     *
     * @return The size of the tribe cards list.
     */
    public int getTribeCardsSize() {
        return this.tribeCards.size();
    }

    /**
     * Retrieves a unified list of all cards (both tribe and building cards)
     * currently present in the upper pile.
     * Primarily used to expose draftable cards to the players.
     *
     * @return A combined list containing all cards in the upper row.
     */
    public List<Card> getAllCards() {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(this.tribeCards);
        allCards.addAll(this.buildingCards);
        return allCards;
    }

    /**
     * Returns the tribe cards currently visible in the upper row preserving their order.
     *
     * @return copy of visible upper tribe cards
     */
    public List<TribeCard> getTribeCards() {
        return new ArrayList<>(this.tribeCards);
    }

    /**
     * Returns the building cards currently visible in the upper row preserving their order.
     *
     * @return copy of visible upper building cards
     */
    public List<BuildingCard> getBuildingCards() {
        return new ArrayList<>(this.buildingCards);
    }
}
