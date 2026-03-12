package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.cards.*;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;
import java.util.stream.Collectors;

public class Board {
    private UpperPile upperPile;
    private LowerPile lowerPile;
    private OfferPath offerPath;

    public Board(UpperPile upperPile, LowerPile lowerPile, OfferPath offerPath) {
        this.upperPile = upperPile;
        this.lowerPile = lowerPile;
        this.offerPath = offerPath;
    }

    public void buildOfferPath() {
        // TODO: Implementare la logica per costruire il percorso delle offerte
    }

    public void setupInitialRows(TribeDeck tribeDeck, BuildingDeck buildingDeck, int playerCount) {
        // TODO: Implementare la logica per preparare le righe iniziali
    }
 //non serve
//    public void discardLowerRow() {
//        if (this.lowerPile != null) {
//            this.lowerPile.discardLowerRow();
//        }
//    }

    /**
     * Fa scorrere tutte le carte Tribù (Personaggi ed Eventi) dalla riga superiore
     * a quella inferiore. Gli edifici presenti nella riga superiore non vengono toccati.
     */
    public void shiftUpperToLower() {
        //elimiamo da sotto (dopo risoluzione eventi)
        this.lowerPile.discardLowerRow();
        // 1. Chiediamo alla riga superiore di darci tutte le sue carte Tribù.
        // (L'UpperPile si occuperà da sola di svuotare la sua lista interna di TribeCard)
        List<TribeCard> tribeCardsToMove = this.upperPile.removeAndReturnAllTribeCards();

        // 2. Passiamo queste carte direttamente alla riga inferiore.
        if (tribeCardsToMove != null && !tribeCardsToMove.isEmpty()) {
            this.lowerPile.addTribeCards(tribeCardsToMove);
        }
        // attenzione va fatto (gestito dal controller) a passetti precisi (prima svuota lower e poi shift)
    }

    public void refillUpperTribeCards(TribeDeck tribeDeck, int playerCount) {
        // TODO: Implementare la logica per riempire le carte tribù nella riga superiore
    }

    public void moveUpperBuildingCardsToLowerRow() {
        // Pulizia automatica prima dello spostamento
        this.lowerPile.discardLowerBuildings();

        // Trasferimento atomico
        List<BuildingCard> buildingsToMove = this.upperPile.removeAndReturnAllBuildingCards();
        if (buildingsToMove != null) {
            this.lowerPile.addBuildingCards(buildingsToMove);
        }
    }

    public void refillUpperBuildingCards(BuildingDeck buildingDeck) {
        // TODO: Implementare la logica per riempire le carte edificio nella riga superiore
    }
    // --- METODI DOVUTI AL PESCAGGIO CHE PARTE DA GAME ---

    /**
     * Restituisce solo le carte della riga superiore che il giocatore può effettivamente prendere.
     */
    //manca il canbedrafted sulla carta
//    public List<Card> getDraftableUpperCards(Player player) {
//        return this.upperPile.getAllCards().stream()
//                .filter(card -> card.canBeDraftedBy(player))
//                .collect(Collectors.toList());
//    }

    /**
     * Restituisce solo le carte della riga inferiore che il giocatore può effettivamente prendere.
     */
//    public List<Card> getDraftableLowerCards(Player player) {
//        return this.lowerPile.getAllCards().stream()
//                .filter(card -> card.canBeDraftedBy(player))
//                .collect(Collectors.toList());
//    }

    // Metodi per permettere al Game di rimuovere la carta una volta acquistata/pescata
    public void removeUpperCard(Card card) {
        this.upperPile.removeCard(card);
    }

    public void removeLowerCard(Card card) {
        this.lowerPile.removeCard(card);
    }
}
