package it.polimi.ingsoft.mesos.server.persistence.game.restore;

import it.polimi.ingsoft.mesos.server.controller.Factory.DeckTemplate;
import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;

import java.util.Map;

/**
 * Resolves card instances from persisted identifiers.
 */
public final class CardInstanceResolver {
    private final DeckTemplate deckTemplate;

    public CardInstanceResolver() {
        this(ParserJson.getDeckTemplate());
    }

    public CardInstanceResolver(DeckTemplate deckTemplate) {
        this.deckTemplate = deckTemplate;
    }

    /**
     * Creates a card instance for the provided id.
     *
     * @param cardId card identifier in persistence snapshots
     * @return fresh card instance
     */
    public Card byId(int cardId) {
        return this.deckTemplate.createCardById(cardId);
    }

    /**
     * Restores a building runtime state when the card supports it.
     *
     * @param card card instance reconstructed from snapshot
     * @param runtimeState map of mutable data stored with the snapshot
     */
    public void restoreRuntimeStateIfSupported(Card card, Map<String, Integer> runtimeState) {
        if (card instanceof BuildingCard buildingCard) {
            buildingCard.restoreRuntimeState(runtimeState);
        }
    }
}

