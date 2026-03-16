package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.cards.*;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Facade class representing the physical game board.
 * Manages the lifecycle of cards in the upper and lower piles, the offer path,
 * and the resolution of end-of-round board events.
 */
public class Board {
    private UpperPile upperPile;
    private LowerPile lowerPile;
    private OfferPath offerPath;

    private TribeDeck tribeDeck;
    private BuildingDeck buildingDeck;
    private List<Player> players;
    private int numPlayer;

    /**
     * Initializes the board by creating empty physical structures and injecting the necessary game data.
     *
     * @param tribeDeck The deck containing tribe cards (characters and events).
     * @param buildingDeck The deck containing building cards.
     * @param players The list of players participating in the game.
     */
    public Board(TribeDeck tribeDeck, BuildingDeck buildingDeck, List<Player> players) {
        this.upperPile = new UpperPile();
        this.lowerPile = new LowerPile();
        this.offerPath = new OfferPath();
        this.tribeDeck = tribeDeck;
        this.buildingDeck = buildingDeck;
        this.players = players;
        this.numPlayer = players.size();
    }

    /**
     * Populates the upper and lower rows with the initial set of cards drawn from the decks.
     * Events are automatically placed in the upper row, while characters fill the lower row first.
     */
    public void setupInitialRows() {
        int targetLowerCharacters = numPlayer + 1;
        while (this.lowerPile.getTribeCardsSize() < targetLowerCharacters) {
            if (this.tribeDeck.isEmpty()) break;

            TribeCard drawnCard = (TribeCard) this.tribeDeck.draw();
            if (drawnCard instanceof EventCard) {
                this.upperPile.addCard(drawnCard);
            } else if (drawnCard instanceof CharacterCard) {
                this.lowerPile.addCard(drawnCard);
            }
        }

        int targetUpperTribeCards = numPlayer + 4;
        int currentUpperTribeCards = this.upperPile.getTribeCardsSize();
        int cardsStillToDraw = targetUpperTribeCards - currentUpperTribeCards;

        for (int i = 0; i < cardsStillToDraw; i++) {
            if (!this.tribeDeck.isEmpty()) {
                TribeCard drawnCard = (TribeCard) this.tribeDeck.draw();
                this.upperPile.addCard(drawnCard);
            }
        }

        int era1BuildingsToDraw = (numPlayer == 2) ? 1 : 2;
        for (int i = 0; i < era1BuildingsToDraw; i++) {
            if (!this.buildingDeck.isEmpty()) {
                BuildingCard drawnBuilding = (BuildingCard) this.buildingDeck.draw();
                this.upperPile.addCard(drawnBuilding);
            }
        }
    }

    /**
     * Initializes the offer spaces and the starting tile blocks based on the player count.
     */
    public void buildOfferPath() {
        this.offerPath.setup(this.players);
    }

    /**
     * Discards all cards currently in the lower row and shifts all tribe cards
     * from the upper row down to the lower row.
     */
    public void shiftUpperToLower() {
        this.lowerPile.discardLowerRow();
        List<TribeCard> tribeCardsToMove = this.upperPile.removeAndReturnAllTribeCards();

        if (tribeCardsToMove != null && !tribeCardsToMove.isEmpty()) {
            this.lowerPile.addTribeCards(tribeCardsToMove);
        }
    }

    /**
     * Discards all building cards currently in the lower row and shifts all building cards
     * from the upper row down to the lower row.
     */
    public void moveUpperBuildingCardsToLowerRow() {
        this.lowerPile.discardLowerBuildings();
        List<BuildingCard> buildingsToMove = this.upperPile.removeAndReturnAllBuildingCards();

        if (buildingsToMove != null && !buildingsToMove.isEmpty()) {
            this.lowerPile.addBuildingCards(buildingsToMove);
        }
    }

    /**
     * Resolves all events present in the lower row, including the sustenance event,
     * applying their effects to all players.
     */
    public void resolveLowerEvents() {
        ArrayList<Player> arrayListPlayers = new ArrayList<>(this.players);
        this.lowerPile.resolveLowerRowEvents(arrayListPlayers);
    }

    /**
     * Extracts the new turn order based on the current totem positions on the offer path,
     * clears the offer spaces, and places players on the starting blocks to resolve end-of-round effects.
     *
     * @return The list of players sorted in the new turn order for the next round.
     */
    public List<Player> rebuildOrderFromOfferPath() {
        return this.offerPath.resetAndGetNewOrder();
    }

    /**
     * Refills the upper row with new tribe cards up to the required amount.
     * If a card from a new era is drawn, it triggers the era change logic.
     *
     * @param currentEra The era currently being played before the refill.
     * @return True if a card from a new era was drawn, false otherwise.
     */
    public boolean refillUpperTribeCards(Era currentEra) {
        int cardsNeeded = (numPlayer + 4) - this.upperPile.getTribeCardsSize();
        boolean newEraTriggered = false;

        for (int i = 0; i < cardsNeeded; i++) {
            if (this.tribeDeck.isEmpty()) break;

            TribeCard drawnCard = (TribeCard) this.tribeDeck.draw();

            if (drawnCard.getEra() != currentEra && !newEraTriggered) {
                newEraTriggered = true;
                this.moveUpperBuildingCardsToLowerRow();
                this.refillUpperBuildingCards(drawnCard.getEra());
            }

            this.upperPile.addCard(drawnCard);
        }

        return newEraTriggered;
    }

    /**
     * Refills the upper row with new building cards based on the current era and player count.
     *
     * @param newEra The era defining how many building cards should be drawn.
     */
    public void refillUpperBuildingCards(Era newEra) {
        int buildingsToDraw = getBuildingCountForEra(newEra);

        for (int i = 0; i < buildingsToDraw; i++) {
            if (!this.buildingDeck.isEmpty()) {
                BuildingCard drawnBuilding = (BuildingCard) this.buildingDeck.draw();
                this.upperPile.addCard(drawnBuilding);
            }
        }
    }

    /**
     * Calculates the required amount of building cards to draw based on the era and player count.
     *
     * @param era The current era.
     * @return The number of building cards to draw.
     */
    private int getBuildingCountForEra(Era era) {
        if (era == Era.second) {
            return (numPlayer <= 3) ? 2 : 3;
        } else if (era == Era.third) {
            if (numPlayer == 2) return 3;
            if (numPlayer == 3 || numPlayer == 4) return 4;
            if (numPlayer == 5) return 5;
        }
        return 0;
    }

    /**
     * Checks if a specific offer space exists and is currently unoccupied.
     *
     * @param targetCode The unique string code of the target offer space (e.g., "A", "B").
     * @return True if the space is available, false otherwise.
     */
    public boolean isSpaceAvailable(String targetCode) {
        return this.offerPath.checkOfferSpace(targetCode);
    }

    /**
     * Places a player's totem on the specified offer space, resolves immediate space effects (e.g., food),
     * and returns the drafting constraints.
     *
     * @param player The player taking the action.
     * @param targetCode The unique string code of the target offer space.
     * @return An integer array where index 0 is the number of upper row cards to draft,
     * and index 1 is the number of lower row cards to draft.
     */
    public int[] placePlayerOnOfferSpace(Player player, String targetCode) {
        return this.offerPath.placePlayer(player, targetCode);
    }

    /**
     * Retrieves all cards currently in the upper row that the specified player is legally allowed to draft.
     *
     * @param player The player attempting to draft.
     * @return A list of draftable cards from the upper row.
     */
    public List<Card> getDraftableUpperCards(Player player) {
        return this.upperPile.getAllCards().stream()
                .filter(card -> this.isCardDraftableForPlayer(card, player))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all cards currently in the lower row that the specified player is legally allowed to draft.
     *
     * @param player The player attempting to draft.
     * @return A list of draftable cards from the lower row.
     */
    public List<Card> getDraftableLowerCards(Player player) {
        return this.lowerPile.getAllCards().stream()
                .filter(card -> this.isCardDraftableForPlayer(card, player))
                .collect(Collectors.toList());
    }

    /**
     * Returns the row currently containing the requested card.
     *
     * @param card The card to locate on the board.
     * @return {@link CardRow#UPPER} if the card is in the upper row,
     * {@link CardRow#LOWER} if it is in the lower row,
     * or {@link CardRow#NOT_PRESENT} if the card is not on the board.
     */
    public CardRow getCardRow(Card card) {
        if (this.getUpperRowCards().contains(card)) {
            return CardRow.UPPER;
        }
        if (this.getLowerRowCards().contains(card)) {
            return CardRow.LOWER;
        }
        return CardRow.NOT_PRESENT;
    }

    /**
     * Returns whether the provided player may currently draw the requested card from the board.
     *
     * @param player player attempting to draw the card
     * @param card requested card
     * @return {@code true} when the card is present on the board and drawable by the player
     */
    public boolean canPlayerDrawCard(Player player, Card card) {
        return this.getCardRow(card) != CardRow.NOT_PRESENT
                && this.isCardDraftableForPlayer(card, player);
    }

    /**
     * Removes a specific drafted card from the upper row.
     *
     * @param card The card to remove.
     */
    public void removeUpperCard(Card card) {
        this.upperPile.removeCard(card);
    }

    /**
     * Removes a specific drafted card from the lower row.
     *
     * @param card The card to remove.
     */
    public void removeLowerCard(Card card) {
        this.lowerPile.removeCard(card);
    }

    /**
     * Retrieves a read-only view of all cards currently present in the upper row.
     *
     * @return A list of all cards in the upper row.
     */
    public List<Card> getUpperRowCards() {
        return this.upperPile.getAllCards();
    }

    /**
     * Retrieves a read-only view of all cards currently present in the lower row.
     *
     * @return A list of all cards in the lower row.
     */
    public List<Card> getLowerRowCards() {
        return this.lowerPile.getAllCards();
    }

    /**
     * Retrieves a read-only view of all offer spaces valid for the current game configuration.
     *
     * @return A list of available offer spaces.
     */
    public List<OfferSpace> getAvailableOfferSpaces() {
        return this.offerPath.getAvailableOfferSpaces();
    }

    private boolean isCardDraftableForPlayer(Card card, Player player) {
        if (card instanceof BuildingCard) {
            BuildingCard buildingCard = (BuildingCard) card;
            return player.getFood() >= buildingCard.getEffectiveFoodCost(player);
        }
        //it's always true
        return card.isDrawable(player);
    }
}
