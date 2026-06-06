package it.polimi.ingsoft.mesos.server.model.game.board;

import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.game.cards.*;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Facade over the physical game board, coordinating its card rows, decks, and offer path.
 *
 * <p>The board exposes two visible card rows: the {@link UpperPile} (upper row) holds the freshly
 * drawn tribe and building cards, while the {@link LowerPile} (lower row) holds the cards that have
 * shifted down from the upper row and is where lower-row events are resolved. New cards are drawn
 * from the {@link TribeDeck} and {@link BuildingDeck}.
 *
 * <p>Player placement happens on the {@link OfferPath}, which owns the offer spaces and the starting
 * tile where players wait between turns. At the end of a round the board shifts the upper row down to
 * the lower row and refills the upper row from the decks, detecting era changes and re-stocking
 * building cards accordingly. It also lets callers resolve board events and look up a visible card by
 * its id via {@link #findCardById(int)}.
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
     * @param tribeDeck    The deck containing tribe cards (characters and events).
     * @param buildingDeck The deck containing building cards.
     * @param players      The list of players participating in the game.
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
    private void shiftUpperToLower() {
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
    private void moveUpperBuildingCardsToLowerRow() {
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
     * Moves a player from their current offer space back to the starting tile.
     * Clears the occupied offer space and automatically places the player on the first available starting block.
     *
     * @param player The player who has just completed their active turn.
     */
    public void returnPlayerToStartingTile(Player player) {
        this.offerPath.returnPlayerToStartingTile(player);
    }

    /**
     * Moves a player to the last available starting tile block.
     *
     * @param player player to move
     */
    public void movePlayerToLastStartingTile(Player player) {
        // offer path to starting tile -> 0
        this.offerPath.movePlayerToLastStartingTile(player,0);
    }

    /**
     * Advances the visible card rows for the next round.
     *
     * <p>Discards the lower row's tribe cards and shifts every tribe card from the upper row down to
     * the lower row, then refills the upper row with new tribe cards drawn from the tribe deck.
     * Offer-path placements and the starting tile are left untouched.
     *
     * @param currentEra era active before resetting the board
     * @return {@code true} when the refill drew a card from a new era, moving the game into it
     */
    public boolean resetBoardAfterRound(Era currentEra) {
        this.shiftUpperToLower();
        return this.refillUpperTribeCards(currentEra);
    }

    /**
     * Refills the upper row with tribe cards drawn from the tribe deck until it holds
     * {@code numPlayer + 4} of them, stopping early if the deck runs out.
     *
     * <p>The first drawn card whose era differs from {@code currentEra} triggers an era change: the
     * upper row's building cards are moved down to the lower row and the upper row is re-stocked with
     * building cards for the new era. Each drawn tribe card is then added to the upper row.
     *
     * @param currentEra the era being played before the refill
     * @return {@code true} if a card from a new era was drawn, {@code false} otherwise
     */
    private boolean refillUpperTribeCards(Era currentEra) {
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
    private void refillUpperBuildingCards(Era newEra) {
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
     * The counts are hardcoded from the game rules.
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
     * Places a player's totem on the specified offer space,
     *
     * @param player     The player taking the action.
     * @param targetCode The unique string code of the target offer space.
     */
    public void placePlayerOnOfferSpace(Player player, String targetCode) {
        this.offerPath.placePlayer(player, targetCode);
    }

    /**
     * Retrieves the list of players currently placed on the offer path,
     * ordered strictly ascending by their space's position index (from left to right skipping A).
     * This method is used by the game controller to determine the exact
     * resolution order of the active turns.
     *
     * @return A list of players sorted by their position on the offer path.
     */
    public List<Player> getResolutionOrder() {
        return this.offerPath.getPlayersInOrder();
    }

    /**
     * Re-packs the players still on the starting tile towards its last blocks.
     *
     * <p>Players are taken in reverse turn order and each is moved to the last free starting block,
     * which frees up the first blocks while preserving the relative order. Landing on a block resolves
     * its effect, so the penalty (malus) attached to the final block is applied to whoever ends up
     * there.
     */
    public void shiftToLastStartingTile()
    {
        // starting tile to starting tile -> 1
        List <Player> PlayersToShift = this.offerPath.getStartingTile().getPlayersOrderedByStartingBlock();
        for (Player player : PlayersToShift.reversed()) {
            this.offerPath.movePlayerToLastStartingTile(player,1);
        }
    }
    /**
     * Retrieves the exact turn order for the next round.
     * This is determined by the order in which players returned to the starting blocks.
     *
     * @return A list of players sorted by their turn order for the upcoming round.
     */
    public List<Player> getNextRoundTurnOrder() {
        return this.offerPath.getNextRoundTurnOrder();
    }

    /**
     * Checks if the offer space 'A' is currently occupied by a player.
     * If occupied, it immediately resolves the space's specific effect for that player
     * and returns it to the starting tile. If the space is empty or unavailable, it does nothing.
     *
     */
    public void resolveEventA()
    {
       this.offerPath.resolveEventA();
    }


    /**
     * Resolves the immediate action for the specified player based on their current
     * offer space and retrieves their specific drafting constraints.
     *
     * @param player The player whose turn action is being resolved.
     * @return An integer array representing the number of cards to draw, where
     * index 0 is the amount from the upper row, and index 1 is from the lower row.
     */
    public int[] returnNumberDrawableCard(Player player) {
        return this.offerPath.resolveActionForPlayer(player);
    }


    /**
     * Retrieves all cards currently in the upper row that the specified player is legally allowed to draft.
     *
     * @param player The player attempting to draft.
     * @return A list of draftable cards from the upper row.
     */
    public List<Card> getDraftableUpperCards(Player player) {
        return this.upperPile.getAllCards().stream()
                .filter(card -> card.isDrawable(player))
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
                .filter(card -> card.isDrawable(player))
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

        if (card == null) {
            return CardRow.NOT_PRESENT;
        }

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
        return this.getCardRow(card) != CardRow.NOT_PRESENT && card.isDrawable(player);
    }

    /**
     * Resolves all event cards currently present on the board (both upper and lower rows).
     * Events are resolved in chronological order based on their {@link Era} (oldest to newest).
     * Ensures that any Sustenance Event is always resolved strictly after all
     * regular events, also adhering to the chronological Era order if multiple are present.
     */
    public void resolveAllEvents() {
        List<SustenanceEventCard> sustenanceEvents = new ArrayList<>();
        List<EventCard> regularEvents = new ArrayList<>();

        List<Card> allVisibleCards = new ArrayList<>();
        allVisibleCards.addAll(this.getUpperRowCards());
        allVisibleCards.addAll(this.getLowerRowCards());

        for (Card card : allVisibleCards) {
            if (card instanceof SustenanceEventCard) {
                sustenanceEvents.add((SustenanceEventCard) card);
            } else if (card instanceof EventCard) {
                regularEvents.add((EventCard) card);
            }
        }

        regularEvents.sort(Comparator.comparing(EventCard::getEra));
        sustenanceEvents.sort(Comparator.comparing(SustenanceEventCard::getEra));

        ArrayList<Player> arrayListPlayers = new ArrayList<>(this.players);
        for (EventCard event : regularEvents) {
            event.applyEffect(arrayListPlayers);
        }
        for (SustenanceEventCard event : sustenanceEvents) {
            event.applyEffect(arrayListPlayers);
        }
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

    /**
     * Returns all offer spaces configured on the board, including occupied ones.
     *
     * @return copy of all offer spaces in path order
     */
    public List<OfferSpace> getOfferSpaces() {
        return this.offerPath.getOfferSpaces();
    }

    /**
     * Returns the cards still present in the tribe deck preserving their draw order.
     *
     * @return copy of remaining tribe-deck cards
     */
    public List<Card> getRemainingTribeDeckCards() {
        return this.tribeDeck.getCardsInOrder();
    }

    /**
     * Returns the cards still present in the building deck preserving their draw order.
     *
     * @return copy of remaining building-deck cards
     */
    public List<Card> getRemainingBuildingDeckCards() {
        return this.buildingDeck.getCardsInOrder();
    }

    /**
     * Returns the starting blocks currently associated with the offer path.
     *
     * @return current starting blocks in positional order
     */
    public List<StartingBlock> getStartingBlocks() {
        return this.offerPath.getStartingTile().getStartingBlocks();
    }
    /**
     * Looks up a visible card on the board by its id, searching the upper row first and then the
     * lower row.
     *
     * @param cardId the id of the card to find
     * @return the matching {@link Card}, or {@code null} if no visible card has that id
     */
    public Card findCardById(int cardId) {
        for (Card card : this.getUpperRowCards()) {
            if (card.getId() == cardId) {
                return card;
            }
        }

        for (Card card : this.getLowerRowCards()) {
            if (card.getId() == cardId) {
                return card;
            }
        }
        return null;
    }
}
