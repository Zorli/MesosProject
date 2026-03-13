package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.cards.*;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;
import java.util.stream.Collectors;

public class Board {
    private UpperPile upperPile;
    private LowerPile lowerPile;
    private OfferPath offerPath;

    private TribeDeck tribeDeck;
    private BuildingDeck buildingDeck;

    public Board(UpperPile upperPile, LowerPile lowerPile, OfferPath offerPath, TribeDeck tribeDeck, BuildingDeck buildingDeck) {
        this.upperPile = upperPile;
        this.lowerPile = lowerPile;
        this.offerPath = offerPath;
        this.tribeDeck = tribeDeck;
        this.buildingDeck = buildingDeck;
    }

    public void buildOfferPath() {
        // TODO: Implementare la logica per costruire il percorso delle offerte
    }

    public void setupInitialRows(int playerCount) {
        int n = playerCount;
        int targetLowerCharacters = n + 1;
        while (this.lowerPile.getTribeCardsSize() < targetLowerCharacters) {
            if (this.tribeDeck.isEmpty()) break; // Sicurezza per non far crashare il gioco

            TribeCard drawnCard = (TribeCard) this.tribeDeck.draw();
            if (drawnCard instanceof EventCard) {
                this.upperPile.addCard(drawnCard);
            } else if (drawnCard instanceof CharacterCard) {
                this.lowerPile.addCard(drawnCard);
            }
        }

        // 2. SETUP UPPER PILE - TRIBE CARDS (Obiettivo: n + 4 TribeCards in totale)
        int targetUpperTribeCards = n + 4;
        int currentUpperTribeCards = this.upperPile.getTribeCardsSize();
        int cardsStillToDraw = targetUpperTribeCards - currentUpperTribeCards;

        for (int i = 0; i < cardsStillToDraw; i++) {
            if (!this.tribeDeck.isEmpty()) {
                TribeCard drawnCard = (TribeCard) this.tribeDeck.draw();
                this.upperPile.addCard(drawnCard);
            }
        }
        // metodo poco elegante forse da modificare
        int era1BuildingsToDraw = (playerCount == 2) ? 1 : 2;

        for (int i = 0; i < era1BuildingsToDraw; i++) {
            if (!this.buildingDeck.isEmpty()) {
                BuildingCard drawnBuilding = (BuildingCard) this.buildingDeck.draw();
                this.upperPile.addCard(drawnBuilding);
            }
        }
    }


    /**
     * Fa scorrere tutte le carte Tribù (Personaggi ed Eventi) dalla riga superiore
     * a quella inferiore. Gli edifici presenti nella riga superiore non vengono toccati.
     */
    public void shiftUpperToLower() {
        //elimiamo da sotto (dopo risoluzione eventi)
        this.lowerPile.discardLowerRow();
        List<TribeCard> tribeCardsToMove = this.upperPile.removeAndReturnAllTribeCards();

        if (tribeCardsToMove != null && !tribeCardsToMove.isEmpty()) {
            this.lowerPile.addTribeCards(tribeCardsToMove);
        }
    }
    //ripartire da qua
    public void refillUpperTribeCards(int playerCount) {
        // TODO: Implementare la logica per riempire le carte tribù nella riga superiore
    }

    public void moveUpperBuildingCardsToLowerRow() {
        this.lowerPile.discardLowerBuildings();
        List<BuildingCard> buildingsToMove = this.upperPile.removeAndReturnAllBuildingCards();
        if (buildingsToMove != null) {
            this.lowerPile.addBuildingCards(buildingsToMove);
        }
    }

    public void refillUpperBuildingCards() {
        // TODO: Implementare la logica per riempire le carte edificio nella riga superiore
    }
    // --- METODI DOVUTI AL PESCAGGIO CHE PARTE DA GAME ---

    /**
     * Restituisce solo le carte della riga superiore che il giocatore può effettivamente prendere.
     */
    public List<Card> getDraftableUpperCards(Player player) {
        return this.upperPile.getAllCards().stream()
                .filter(card -> card.isDrawable(player))
                .collect(Collectors.toList());
    }

    /**
     * Restituisce solo le carte della riga inferiore che il giocatore può effettivamente prendere.
     */
    public List<Card> getDraftableLowerCards(Player player) {
        return this.lowerPile.getAllCards().stream()
                .filter(card -> card.isDrawable(player))
                .collect(Collectors.toList());
    }

    // Metodi per permettere al Game di rimuovere la carta una volta acquistata/pescata
    public void removeUpperCard(Card card) {
        this.upperPile.removeCard(card);
    }

    public void removeLowerCard(Card card) {
        this.lowerPile.removeCard(card);
    }
}
