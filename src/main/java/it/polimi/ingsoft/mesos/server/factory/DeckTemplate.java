package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Immutable catalog of card blueprints loaded from the static deck JSON.
 * <p>
 * The template stores suppliers instead of concrete card instances so that each
 * match receives independent objects, while the expensive JSON parsing happens
 * only once during server lifetime.
 */
public final class DeckTemplate {
    private final List<TribeCardBlueprint> tribeCardBlueprints;
    private final List<BuildingCardBlueprint> buildingCardBlueprints;

    /**
     * Creates a new immutable template from the provided tribe and building blueprints.
     *
     * @param tribeCardBlueprints blueprints for tribe cards and events
     * @param buildingCardBlueprints blueprints for building cards
     */
    DeckTemplate(List<TribeCardBlueprint> tribeCardBlueprints, List<BuildingCardBlueprint> buildingCardBlueprints) {
        this.tribeCardBlueprints = List.copyOf(tribeCardBlueprints);
        this.buildingCardBlueprints = List.copyOf(buildingCardBlueprints);
    }

    /**
     * Wraps a tribe card supplier together with its optional player-count constraint.
     *
     * @param cardId identifier assigned to the card
     * @param minPlayers minimum player count required for the card, or {@code null} if always available
     * @param factory supplier that creates a fresh tribe card instance
     * @return a tribe card blueprint
     */
    static TribeCardBlueprint tribeCard(int cardId, Integer minPlayers, Supplier<? extends TribeCard> factory) {
        return new TribeCardBlueprint(cardId, minPlayers, factory);
    }

    /**
     * Wraps a building card supplier in a blueprint.
     *
     * @param cardId identifier assigned to the card
     * @param factory supplier that creates a fresh building card instance
     * @return a building card blueprint
     */
    static BuildingCardBlueprint buildingCard(int cardId, Supplier<? extends BuildingCard> factory) {
        return new BuildingCardBlueprint(cardId, factory);
    }

    /**
     * Instantiates the full tribe deck stored in the template.
     *
     * @return a fresh tribe deck instance
     */
    public TribeDeck createTribeDeck() {
        ArrayList<Card> cards = new ArrayList<>();
        for (TribeCardBlueprint blueprint : this.tribeCardBlueprints) {
            cards.add(blueprint.instantiate());
        }
        return new TribeDeck(cards);
    }

    /**
     * Instantiates the tribe deck valid for a specific player count.
     *
     * @param playerCount number of players in the match, expected between 2 and 5
     * @return a fresh tribe deck filtered by blueprint availability
     * @throws IllegalArgumentException if {@code playerCount} is outside the supported range
     */
    public TribeDeck createTribeDeck(int playerCount) {
        validatePlayerCount(playerCount);

        ArrayList<Card> cards = new ArrayList<>();
        for (TribeCardBlueprint blueprint : this.tribeCardBlueprints) {
            if (blueprint.isAvailableFor(playerCount)) {
                cards.add(blueprint.instantiate());
            }
        }
        return new TribeDeck(cards);
    }

    /**
     * Instantiates the complete building deck stored in the template.
     *
     * @return a fresh building deck instance
     */
    public BuildingDeck createBuildingDeck() {
        ArrayList<Card> cards = new ArrayList<>();
        for (BuildingCardBlueprint blueprint : this.buildingCardBlueprints) {
            cards.add(blueprint.instantiate());
        }
        return new BuildingDeck(cards);
    }

    /**
     * Instantiates a fresh card matching the provided identifier from the immutable template.
     *
     * @param cardId identifier of the card to instantiate
     * @return fresh card instance
     */
    public Card createCardById(int cardId) {
        for (TribeCardBlueprint blueprint : this.tribeCardBlueprints) {
            if (blueprint.hasId(cardId)) {
                return blueprint.instantiate();
            }
        }

        for (BuildingCardBlueprint blueprint : this.buildingCardBlueprints) {
            if (blueprint.hasId(cardId)) {
                return blueprint.instantiate();
            }
        }

        throw new IllegalArgumentException("Unknown card id: " + cardId);
    }

    /**
     * Returns how many tribe card blueprints are stored in the template.
     *
     * @return tribe card template count
     */
    public int getTribeCardTemplateCount() {
        return this.tribeCardBlueprints.size();
    }

    /**
     * Returns how many building card blueprints are stored in the template.
     *
     * @return building card template count
     */
    public int getBuildingCardTemplateCount() {
        return this.buildingCardBlueprints.size();
    }

    /**
     * Rejects unsupported player counts before filtering tribe card blueprints.
     *
     * @param playerCount number of players requested for the match
     */
    private static void validatePlayerCount(int playerCount) {
        if (playerCount < 2 || playerCount > 5) {
            throw new IllegalArgumentException("Player count must be between 2 and 5");
        }
    }

    /**
     * Stores the minimum player requirement and the supplier for a tribe card blueprint.
     */
    static final class TribeCardBlueprint {
        private final int cardId;
        private final Integer minPlayers;
        private final Supplier<? extends TribeCard> factory;

        /**
         * Creates a blueprint for a tribe card that can later be instantiated on demand.
         *
         * @param cardId identifier assigned to the card
         * @param minPlayers minimum supported player count, or {@code null} when always available
         * @param factory supplier that creates the concrete card instance
         */
        private TribeCardBlueprint(int cardId, Integer minPlayers, Supplier<? extends TribeCard> factory) {
            this.cardId = cardId;
            this.minPlayers = minPlayers;
            this.factory = factory;
        }

        /**
         * Checks whether this blueprint represents the requested card identifier.
         *
         * @param cardId requested card identifier
         * @return {@code true} when this blueprint can instantiate that card
         */
        private boolean hasId(int cardId) {
            return this.cardId == cardId;
        }

        /**
         * Checks whether this card should appear in a match with the given player count.
         *
         * @param playerCount number of players in the match
         * @return {@code true} when the card is enabled for that player count
         */
        private boolean isAvailableFor(int playerCount) {
            return this.minPlayers == null || this.minPlayers <= playerCount;
        }

        /**
         * Produces a fresh tribe card instance from the stored supplier.
         *
         * @return a new tribe card
         */
        private TribeCard instantiate() {
            return this.factory.get();
        }
    }

    /**
     * Stores the supplier used to instantiate a building card on demand.
     */
    static final class BuildingCardBlueprint {
        private final int cardId;
        private final Supplier<? extends BuildingCard> factory;

        /**
         * Creates a blueprint for a building card.
         *
         * @param cardId identifier assigned to the card
         * @param factory supplier that creates the concrete card instance
         */
        private BuildingCardBlueprint(int cardId, Supplier<? extends BuildingCard> factory) {
            this.cardId = cardId;
            this.factory = factory;
        }

        /**
         * Checks whether this blueprint represents the requested card identifier.
         *
         * @param cardId requested card identifier
         * @return {@code true} when this blueprint can instantiate that card
         */
        private boolean hasId(int cardId) {
            return this.cardId == cardId;
        }

        /**
         * Produces a fresh building card instance from the stored supplier.
         *
         * @return a new building card
         */
        private BuildingCard instantiate() {
            return this.factory.get();
        }
    }
}
