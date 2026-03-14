package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.events.EventAction;
import it.polimi.ingsoft.mesos.server.model.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

public class LowerPile {
    private List<TribeCard> tribeCards;
    private List<BuildingCard> buildingCards;

    public LowerPile() {
        this.tribeCards = new ArrayList<>();
        this.buildingCards = new ArrayList<>();
    }

    public void addTribeCards(List<TribeCard> cards) {
        this.tribeCards.addAll(cards);
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

    /**
     * Scarta SOLO le carte Tribù (Personaggi ed Eventi) dalla riga inferiore.
     * I Building rimangono intatti al loro posto.
     */
    public void discardLowerRow() {
        this.tribeCards.clear();
    }

    /**
     * Scarta SOLO gli edifici dalla riga inferiore.
     */
    public void discardLowerBuildings() {
        this.buildingCards.clear();
    }

    /**
     * Risolve gli eventi presenti nella riga inferiore.
     * La SustenanceEventCard viene risolta sempre per ultima.
     */
    public void resolveLowerRowEvents(ArrayList<Player> players) {
        SustenanceEventCard sustenanceEvent = null;
        for (TribeCard card : this.tribeCards) {

            if (card instanceof SustenanceEventCard) {
                sustenanceEvent = (SustenanceEventCard) card;

            } else if (card instanceof EventCard) {
                EventCard event = (EventCard) card; //not actually necessary
                event.applyEffect(players);
            }
        }

        // 2. Risolviamo l'evento di Sostentamento per ultimo (se presente)
        if (sustenanceEvent != null) {
            sustenanceEvent.applyEffect(players);
        }
    }

    public void removeCard(Card card) {
        if (card instanceof TribeCard) {
            this.tribeCards.remove(card);
        } else if (card instanceof BuildingCard) {
            this.buildingCards.remove(card);
        }
    }
}
