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

public class Board {
    private UpperPile upperPile;
    private LowerPile lowerPile;
    private OfferPath offerPath;

    private TribeDeck tribeDeck;
    private BuildingDeck buildingDeck;
    private List<Player> players;
    private int numPlayer;

    // ==========================================
    // CONSTRUCTOR & INITIAL SETUP
    // ==========================================

    public Board(TribeDeck tribeDeck, BuildingDeck buildingDeck, List<Player> players) {
        this.upperPile = new UpperPile();
        this.lowerPile = new LowerPile();
        this.offerPath = new OfferPath();
        this.tribeDeck = tribeDeck;
        this.buildingDeck = buildingDeck;
        this.players = players;
        this.numPlayer = players.size();
    }

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

    public void buildOfferPath() {
        this.offerPath.setup(this.players);
    }


    // ==========================================
    // SHIFTING LOGIC (END OF ROUND/ERA)
    // ==========================================

    public void shiftUpperToLower() {
        this.lowerPile.discardLowerRow();
        List<TribeCard> tribeCardsToMove = this.upperPile.removeAndReturnAllTribeCards();

        if (tribeCardsToMove != null && !tribeCardsToMove.isEmpty()) {
            this.lowerPile.addTribeCards(tribeCardsToMove);
        }
    }

    public void moveUpperBuildingCardsToLowerRow() {
        this.lowerPile.discardLowerBuildings();
        List<BuildingCard> buildingsToMove = this.upperPile.removeAndReturnAllBuildingCards();

        if (buildingsToMove != null && !buildingsToMove.isEmpty()) {
            this.lowerPile.addBuildingCards(buildingsToMove);
        }
    }

    public void resolveLowerEvents() {
        ArrayList<Player> arrayListPlayers = new ArrayList<>(this.players);
        this.lowerPile.resolveLowerRowEvents(arrayListPlayers);
    }
    // FOR TURNORDERTRACK //
    public List<Player> rebuildOrderFromOfferPath() {
        return this.offerPath.resetAndGetNewOrder();
    }


    // ==========================================
    // REFILLING LOGIC
    // ==========================================

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

    public void refillUpperBuildingCards(Era newEra) {
        int buildingsToDraw = getBuildingCountForEra(newEra);

        for (int i = 0; i < buildingsToDraw; i++) {
            if (!this.buildingDeck.isEmpty()) {
                BuildingCard drawnBuilding = (BuildingCard) this.buildingDeck.draw();
                this.upperPile.addCard(drawnBuilding);
            }
        }
    }

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


    // ==========================================
    // DRAFTING & PLAYER INTERACTION
    // ==========================================
    public boolean isSpaceAvailable(String targetCode) {
        return this.offerPath.checkOfferSpace(targetCode);
    }

    public int[] placePlayerOnOfferSpace(Player player, String targetCode) {
        return this.offerPath.placePlayer(player, targetCode);
    }

    public List<Card> getDraftableUpperCards(Player player) {
        return this.upperPile.getAllCards().stream()
                .filter(card -> card.isDrawable(player))
                .collect(Collectors.toList());
    }

    public List<Card> getDraftableLowerCards(Player player) {
        return this.lowerPile.getAllCards().stream()
                .filter(card -> card.isDrawable(player))
                .collect(Collectors.toList());
    }

    public void removeUpperCard(Card card) {
        this.upperPile.removeCard(card);
    }
    public void removeLowerCard(Card card) {
        this.lowerPile.removeCard(card);
    }

    // ==========================================
    // GETTERS FOR VIEWS OR GAME (READ ONLY)
    // ==========================================

    public List<Card> getUpperRowCards() {
        return this.upperPile.getAllCards();
    }

    public List<Card> getLowerRowCards() {
        return this.lowerPile.getAllCards();
    }


    public List<OfferSpace> getAvailableOfferSpaces() {
        return this.offerPath.getAvailableOfferSpaces();
    }
}