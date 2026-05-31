package it.polimi.ingsoft.mesos.server.factory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.BigPpReward;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.CavePaintingBonus;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.HunterBonus;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.MultiplierForType;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.PrestigePointsForSet;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.ShamanStarsBonus;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.SustenanceDiscount;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Artist;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.CavePaintingEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.ShamanRitualEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

/**
 * Loads the static deck description from {@code deckSetup.json} and exposes
 * factories that build fresh in-memory decks for a match.
 * <p>
 * The JSON resource is parsed once per process and translated into a
 * {@link DeckTemplate}. Each deck request then instantiates new card objects,
 * so matches never share mutable card state.
 */
public final class ParserJson {
    private static final String DECK_SETUP_RESOURCE_PATH =
            "/it/polimi/ingsoft/mesos/server/model/Factory/deckSetup.json";
    private static final Gson GSON = new Gson();

    private static final class DeckTemplateHolder {
        private static final DeckTemplate TEMPLATE = loadDeckTemplate();

        /**
         * Prevents instantiation of the holder used for lazy initialization.
         */
        private DeckTemplateHolder() {
        }
    }

    /**
     * Prevents instantiation of this static utility class.
     */
    private ParserJson() {
    }

    /**
     * Returns the lazily initialized immutable template built from the deck JSON.
     *
     * @return the shared deck template used as blueprint for new match decks
     */
    public static DeckTemplate getDeckTemplate() {
        return DeckTemplateHolder.TEMPLATE;
    }

    /**
     * Builds a tribe deck containing every character and event card defined in the template.
     *
     * @return a fresh tribe deck instance
     */
    public static TribeDeck parseTribeDeck() {
        return getDeckTemplate().createTribeDeck();
    }

    /**
     * Builds the tribe deck valid for the given player count.
     *
     * @param playerCount number of players in the match, expected between 2 and 5
     * @return a fresh tribe deck filtered by {@code minPlayers}
     */
    public static TribeDeck parseTribeDeck(int playerCount) {
        return getDeckTemplate().createTribeDeck(playerCount);
    }

    /**
     * Builds the complete building deck defined in the template.
     *
     * @return a fresh building deck instance
     */
    public static BuildingDeck parseBuildingDeck() {
        return getDeckTemplate().createBuildingDeck();
    }

    /**
     * Reads the JSON resource and converts it into the immutable deck template used by the preLobby.
     *
     * @return the parsed deck template
     */
    private static DeckTemplate loadDeckTemplate() {
        JsonObject cardsRoot = requiredObject(readDeckSetupRoot(), "cards");
        ArrayList<DeckTemplate.TribeCardBlueprint> tribeCardBlueprints = new ArrayList<>();
        ArrayList<DeckTemplate.BuildingCardBlueprint> buildingCardBlueprints = new ArrayList<>();

        appendCharacterBlueprints(tribeCardBlueprints, requiredObject(cardsRoot, "characters"));
        appendEventBlueprints(tribeCardBlueprints, requiredObject(cardsRoot, "events"));
        appendBuildingBlueprints(buildingCardBlueprints, requiredObject(cardsRoot, "buildings"));

        return new DeckTemplate(tribeCardBlueprints, buildingCardBlueprints);
    }

    /**
     * Opens and parses the raw JSON file that describes all deck contents.
     *
     * @return the root JSON node read from the classpath resource
     */
    private static JsonObject readDeckSetupRoot() {
        try (InputStream inputStream = ParserJson.class.getResourceAsStream(DECK_SETUP_RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing deck setup resource: " + DECK_SETUP_RESOURCE_PATH);
            }
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse deck setup resource", exception);
        }
    }

    /**
     * Extracts all character definitions from the JSON and stores them as lazy tribe-card
     * blueprints.
     *
     * @param tribeCardBlueprints destination list that will receive the generated blueprints
     * @param characterGroups JSON node containing character cards grouped by type
     */
    private static void appendCharacterBlueprints(
            ArrayList<DeckTemplate.TribeCardBlueprint> tribeCardBlueprints,
            JsonObject characterGroups
    ) {
        for (Map.Entry<String, JsonElement> entry : characterGroups.entrySet()) {
            TribeCardType type = TribeCardType.valueOf(entry.getKey());
            for (JsonElement characterElement : requiredArray(entry.getValue(), "cards.characters." + type.name())) {
                JsonObject characterNode = requireObject(characterElement, "cards.characters." + type.name());
                int id = requiredInt(characterNode, "id");
                JsonElement minPlayersNode = characterNode.get("minPlayers");
                Integer minPlayers = (minPlayersNode == null || minPlayersNode.isJsonNull()) ? null : minPlayersNode.getAsInt();
                tribeCardBlueprints.add(DeckTemplate.tribeCard(id, minPlayers, buildCharacterFactory(type, characterNode)));
            }
        }
    }

    /**
     * Extracts all event card definitions from the JSON and stores them as lazy tribe-card
     * blueprints.
     *
     * @param tribeCardBlueprints destination list that will receive the generated blueprints
     * @param eventGroups JSON node containing event cards grouped by family
     */
    private static void appendEventBlueprints(
            ArrayList<DeckTemplate.TribeCardBlueprint> tribeCardBlueprints,
            JsonObject eventGroups
    ) {
        for (Map.Entry<String, JsonElement> entry : eventGroups.entrySet()) {
            String family = entry.getKey();
            for (JsonElement eventElement : requiredArray(entry.getValue(), "cards.events." + family)) {
                JsonObject eventNode = requireObject(eventElement, "cards.events." + family);
                int id = requiredInt(eventNode, "id");
                tribeCardBlueprints.add(DeckTemplate.tribeCard(id, null, buildEventFactory(family, eventNode)));
            }
        }
    }

    /**
     * Extracts all building card definitions from the JSON and stores them as lazy building-card
     * blueprints.
     *
     * @param buildingCardBlueprints destination list that will receive the generated blueprints
     * @param buildingGroups JSON node containing building cards grouped by benefit type
     */
    private static void appendBuildingBlueprints(
            ArrayList<DeckTemplate.BuildingCardBlueprint> buildingCardBlueprints,
            JsonObject buildingGroups
    ) {
        for (Map.Entry<String, JsonElement> entry : buildingGroups.entrySet()) {
            BenefitType benefitType = BenefitType.valueOf(entry.getKey());
            for (JsonElement buildingElement : requiredArray(entry.getValue(), "cards.buildings." + benefitType.name())) {
                JsonObject buildingNode = requireObject(buildingElement, "cards.buildings." + benefitType.name());
                int id = requiredInt(buildingNode, "id");
                buildingCardBlueprints.add(DeckTemplate.buildingCard(id, buildBuildingFactory(benefitType, buildingNode)));
            }
        }
    }

    /**
     * Builds the supplier that recreates one character card as defined in JSON.
     *
     * @param type logical tribe-card type associated with the card definition
     * @param cardNode JSON node containing the card data
     * @return a supplier that creates the corresponding character card
     */
    private static java.util.function.Supplier<? extends TribeCard> buildCharacterFactory(
            TribeCardType type,
            JsonObject cardNode
    ) {
        int id = requiredInt(cardNode, "id");
        Era era = Era.valueOf(requiredText(cardNode, "era"));

        return switch (type) {
            case HUNTER -> {
                boolean hasEffect = requiredBoolean(requiredModel(cardNode), "hasEffect");
                yield () -> new Hunter(id, era, hasEffect, type);
            }
            case BUILDER -> {
                JsonObject modelNode = requiredModel(cardNode);
                int priceDiscount = requiredInt(modelNode, "priceDiscount");
                int prestigePoints = requiredInt(modelNode, "prestigePoints");
                yield () -> new Builder(id, era, type, priceDiscount, prestigePoints);
            }
            case GATHERER -> () -> new Gatherer(id, era, type);
            case ARTIST -> () -> new Artist(id, era, type);
            case INVENTOR -> {
                InventionType inventionType = InventionType.valueOf(
                        requiredText(requiredModel(cardNode), "inventionType")
                );
                yield () -> new Inventor(id, era, type, inventionType);
            }
            case SHAMAN -> {
                int stars = requiredInt(requiredModel(cardNode), "stars");
                yield () -> new Shaman(id, era, stars, type);
            }
            case EVENT -> throw new IllegalArgumentException("Character card " + id + " cannot have type EVENT");
        };
    }

    /**
     * Builds the supplier that recreates one event card as defined in JSON.
     *
     * @param family event family name from the JSON file
     * @param eventNode JSON node containing the event definition
     * @return a supplier that creates the corresponding event card
     */
    private static java.util.function.Supplier<? extends TribeCard> buildEventFactory(String family, JsonObject eventNode) {
        int id = requiredInt(eventNode, "id");
        Era era = Era.valueOf(requiredText(eventNode, "era"));
        JsonObject modelNode = requiredObject(eventNode, "model");

        return switch (family) {
            case "HUNT" -> {
                int foodReward = requiredInt(modelNode, "foodReward");
                int prestigePointsReward = requiredInt(modelNode, "prestigePointsReward");
                yield () -> new HuntEventCard(id, era, TribeCardType.EVENT, foodReward, prestigePointsReward);
            }
            case "SUSTENANCE" -> {
                int prestigePointsToRemovePerCharacter = requiredInt(modelNode, "prestigePointsToRemovePerCharacter");
                yield () -> new SustenanceEventCard(
                        id,
                        era,
                        TribeCardType.EVENT,
                        prestigePointsToRemovePerCharacter
                );
            }
            case "CAVE_PAINTING" -> {
                int limitForPositive = requiredInt(modelNode, "limitForPositive");
                int prestigePointsForPositive = requiredInt(modelNode, "prestigePointsForPositive");
                int prestigePointsForNegative = requiredInt(modelNode, "prestigePointsForNegative");
                yield () -> new CavePaintingEventCard(
                        id,
                        era,
                        limitForPositive,
                        TribeCardType.EVENT,
                        prestigePointsForPositive,
                        prestigePointsForNegative
                );
            }
            case "SHAMAN_RITUAL" -> {
                int prestigePointsForHigher = requiredInt(modelNode, "prestigePointsForHigher");
                int prestigePointsForLower = requiredInt(modelNode, "prestigePointsForLower");
                yield () -> new ShamanRitualEventCard(
                        id,
                        era,
                        TribeCardType.EVENT,
                        prestigePointsForHigher,
                        prestigePointsForLower
                );
            }
            default -> throw new IllegalArgumentException("Unsupported event family: " + family);
        };
    }

    /**
     * Builds the supplier that recreates one building card as defined in JSON.
     *
     * @param benefitType benefit category encoded in the JSON file
     * @param buildingNode JSON node containing the building definition
     * @return a supplier that creates the corresponding building card
     */
    private static java.util.function.Supplier<? extends BuildingCard> buildBuildingFactory(
            BenefitType benefitType,
            JsonObject buildingNode
    ) {
        int id = requiredInt(buildingNode, "id");
        Era era = Era.valueOf(requiredText(buildingNode, "era"));
        JsonObject modelNode = requiredObject(buildingNode, "model");
        int foodCost = requiredInt(modelNode, "foodCost");
        int prestigePoints = requiredInt(modelNode, "prestigePoints");

        return switch (benefitType) {
            case POST_DRAW_SET_FOR_FOOD -> {
                int rewardFood = requiredInt(modelNode, "rewardFood");
                yield () -> new SetForFood(id, era, foodCost, prestigePoints, benefitType, rewardFood);
            }
            case POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION -> {
                int rewardFood = requiredInt(modelNode, "rewardFood");
                yield () -> new PairOfInventor(id, era, foodCost, prestigePoints, benefitType, rewardFood);
            }
            case EVENT_BONUS_SUSTENANCE_DISCOUNT_FOR_TYPE -> {
                int discountAmount = requiredInt(modelNode, "discountAmount");
                TribeCardType applicableType = TribeCardType.valueOf(requiredText(modelNode, "applicableTribeCardType"));
                yield () -> new SustenanceDiscount(
                        id,
                        era,
                        foodCost,
                        prestigePoints,
                        benefitType,
                        discountAmount,
                        applicableType
                );
            }
            case EVENT_BONUS_STARS_SHAMAN -> {
                int starsBonus = requiredInt(modelNode, "starsBonus");
                yield () -> new ShamanStarsBonus(id, era, foodCost, prestigePoints, benefitType, starsBonus);
            }
            case EVENT_BONUS_HUNTER -> {
                int bonusFoodPerHunter = requiredInt(modelNode, "bonusFoodPerHunter");
                int bonusPrestigePointsPerHunter = requiredInt(modelNode, "bonusPrestigePointsPerHunter");
                yield () -> new HunterBonus(
                        id,
                        era,
                        foodCost,
                        prestigePoints,
                        benefitType,
                        bonusFoodPerHunter,
                        bonusPrestigePointsPerHunter
                );
            }
            case EVENT_BONUS_CAVE_PAINTING -> {
                int bonusFoodPerArtist = requiredInt(modelNode, "bonusFoodPerArtist");
                yield () -> new CavePaintingBonus(
                        id,
                        era,
                        foodCost,
                        prestigePoints,
                        benefitType,
                        bonusFoodPerArtist
                );
            }
            case FINAL_PRESTIGE_POINTS_FOR_SET -> {
                int pointsPerSet = requiredInt(modelNode, "rewardPrestigePoints");
                yield () -> new PrestigePointsForSet(id, era, foodCost, prestigePoints, benefitType, pointsPerSet);
            }
            case FINAL_BONUS_MULTIPLIER_FOR_TYPE -> {
                int multiplier = requiredInt(modelNode, "multiplier");
                TribeCardType applicableType = TribeCardType.valueOf(requiredText(modelNode, "applicableTribeCardType"));
                yield () -> new MultiplierForType(id, era, foodCost, prestigePoints, benefitType, multiplier, applicableType);
            }
            case FINAL_BONUS_BIG_PP_REWARD -> {
                int ppReward = requiredInt(modelNode, "rewardPrestigePoints");
                yield () -> new BigPpReward(id, era, foodCost, prestigePoints, benefitType, ppReward);
            }
            case EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT,
                 EVENT_BONUS_SHAMAN_WIN_MULTIPLIER,
                 END_ROUND_EXTRA_CARD,
                 END_ROUND_TOTEM_POSITION,
                 BUILDER_TOTAL_PP_MULTIPLIER -> () -> new BuildingCard(id, era, foodCost, prestigePoints, benefitType);
        };
    }

    /**
     * Returns a required child node and fails fast when the field is missing.
     *
     * @param node parent node
     * @param fieldName required child field name
     * @return the required child node
     */
    private static JsonElement required(JsonObject node, String fieldName) {
        if (node == null) {
            throw new IllegalArgumentException("Missing required object containing field '" + fieldName + "'");
        }

        JsonElement child = node.get(fieldName);
        if (child == null || child.isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + fieldName + "'");
        }
        return child;
    }

    /**
     * Reads a required integer field from the provided node.
     *
     * @param node parent node
     * @param fieldName required integer field name
     * @return the integer value stored in the field
     */
    private static int requiredInt(JsonObject node, String fieldName) {
        return required(node, fieldName).getAsInt();
    }

    /**
     * Reads a required boolean field from the provided node.
     *
     * @param node parent node
     * @param fieldName required boolean field name
     * @return the boolean value stored in the field
     */
    private static boolean requiredBoolean(JsonObject node, String fieldName) {
        return required(node, fieldName).getAsBoolean();
    }

    /**
     * Reads a required text field from the provided node.
     *
     * @param node parent node
     * @param fieldName required text field name
     * @return the text value stored in the field
     */
    private static String requiredText(JsonObject node, String fieldName) {
        return required(node, fieldName).getAsString();
    }

    /**
     * Ensures that the provided node exists and is a JSON object.
     *
     * @param node node to validate
     * @param fieldName logical field name used in validation errors
     * @return the same node when it is a valid object
     */
    private static JsonObject requiredObject(JsonObject node, String fieldName) {
        return requireObject(required(node, fieldName), fieldName);
    }

    /**
     * Ensures that the provided element exists and is a JSON object.
     *
     * @param element element to validate
     * @param fieldName logical field name used in validation errors
     * @return the same element as a valid object
     */
    private static JsonObject requireObject(JsonElement element, String fieldName) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing required object '" + fieldName + "'");
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Expected object for '" + fieldName + "'");
        }
        return element.getAsJsonObject();
    }

    /**
     * Reads the required card model object.
     *
     * @param cardNode card definition node
     * @return the required model object
     */
    private static JsonObject requiredModel(JsonObject cardNode) {
        return requiredObject(cardNode, "model");
    }

    /**
     * Ensures that the provided node is a JSON array.
     *
     * @param node node to validate
     * @param fieldName logical field name used in validation errors
     * @return the same node when it is a valid array
     */
    private static JsonArray requiredArray(JsonElement element, String fieldName) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing required array '" + fieldName + "'");
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Expected array for '" + fieldName + "'");
        }
        return element.getAsJsonArray();
    }

}
