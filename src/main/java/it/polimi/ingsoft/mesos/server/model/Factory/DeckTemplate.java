package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;

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
     * @param minPlayers minimum player count required for the card, or {@code null} if always available
     * @param factory supplier that creates a fresh tribe card instance
     * @return a tribe card blueprint
     */
    static TribeCardBlueprint tribeCard(Integer minPlayers, Supplier<? extends TribeCard> factory) {
        return new TribeCardBlueprint(minPlayers, factory);
    }

    /**
     * Wraps a building card supplier in a blueprint.
     *
     * @param factory supplier that creates a fresh building card instance
     * @return a building card blueprint
     */
    static BuildingCardBlueprint buildingCard(Supplier<? extends BuildingCard> factory) {
        return new BuildingCardBlueprint(factory);
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

    private static void validatePlayerCount(int playerCount) {
        if (playerCount < 2 || playerCount > 5) {
            throw new IllegalArgumentException("Player count must be between 2 and 5");
        }
    }

    static final class TribeCardBlueprint {
        private final Integer minPlayers;
        private final Supplier<? extends TribeCard> factory;

        private TribeCardBlueprint(Integer minPlayers, Supplier<? extends TribeCard> factory) {
            this.minPlayers = minPlayers;
            this.factory = factory;
        }

        private boolean isAvailableFor(int playerCount) {
            return this.minPlayers == null || this.minPlayers <= playerCount;
        }

        private TribeCard instantiate() {
            return this.factory.get();
        }
    }

    static final class BuildingCardBlueprint {
        private final Supplier<? extends BuildingCard> factory;

        private BuildingCardBlueprint(Supplier<? extends BuildingCard> factory) {
            this.factory = factory;
        }

        private BuildingCard instantiate() {
            return this.factory.get();
        }
    }
}
