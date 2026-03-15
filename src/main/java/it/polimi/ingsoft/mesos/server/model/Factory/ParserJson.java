package it.polimi.ingsoft.mesos.server.model.Factory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.CavePaintingBonus;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.HunterBonus;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.ShamanStarsBonus;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SustenanceDiscount;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Artist;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.cards.events.CavePaintingEventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.ShamanRitualEventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final class DeckTemplateHolder {
        private static final DeckTemplate TEMPLATE = loadDeckTemplate();

        private DeckTemplateHolder() {
        }
    }

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

    private static DeckTemplate loadDeckTemplate() {
        JsonNode cardsRoot = required(readDeckSetupRoot(), "cards");
        ArrayList<DeckTemplate.TribeCardBlueprint> tribeCardBlueprints = new ArrayList<>();
        ArrayList<DeckTemplate.BuildingCardBlueprint> buildingCardBlueprints = new ArrayList<>();

        appendCharacterBlueprints(tribeCardBlueprints, required(cardsRoot, "characters"));
        appendEventBlueprints(tribeCardBlueprints, required(cardsRoot, "events"));
        appendBuildingBlueprints(buildingCardBlueprints, required(cardsRoot, "buildings"));

        return new DeckTemplate(tribeCardBlueprints, buildingCardBlueprints);
    }

    private static JsonNode readDeckSetupRoot() {
        try (InputStream inputStream = ParserJson.class.getResourceAsStream(DECK_SETUP_RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing deck setup resource: " + DECK_SETUP_RESOURCE_PATH);
            }
            return OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse deck setup resource", exception);
        }
    }

    private static void appendCharacterBlueprints(
            ArrayList<DeckTemplate.TribeCardBlueprint> tribeCardBlueprints,
            JsonNode characterGroups
    ) {
        appendGroupedRecords(characterGroups, "characters", entry -> {
            TribeCardType type = TribeCardType.valueOf(entry.getKey());
            for (JsonNode characterNode : requiredArray(entry.getValue(), "cards.characters." + type.name())) {
                JsonNode minPlayersNode = characterNode.get("minPlayers");
                Integer minPlayers = (minPlayersNode == null || minPlayersNode.isNull()) ? null : minPlayersNode.asInt();
                tribeCardBlueprints.add(DeckTemplate.tribeCard(minPlayers, buildCharacterFactory(type, characterNode)));
            }
        });
    }

    private static void appendEventBlueprints(
            ArrayList<DeckTemplate.TribeCardBlueprint> tribeCardBlueprints,
            JsonNode eventGroups
    ) {
        appendGroupedRecords(eventGroups, "events", entry -> {
            String family = entry.getKey();
            for (JsonNode eventNode : requiredArray(entry.getValue(), "cards.events." + family)) {
                tribeCardBlueprints.add(DeckTemplate.tribeCard(null, buildEventFactory(family, eventNode)));
            }
        });
    }

    private static void appendBuildingBlueprints(
            ArrayList<DeckTemplate.BuildingCardBlueprint> buildingCardBlueprints,
            JsonNode buildingGroups
    ) {
        appendGroupedRecords(buildingGroups, "buildings", entry -> {
            BenefitType benefitType = BenefitType.valueOf(entry.getKey());
            for (JsonNode buildingNode : requiredArray(entry.getValue(), "cards.buildings." + benefitType.name())) {
                buildingCardBlueprints.add(DeckTemplate.buildingCard(buildBuildingFactory(benefitType, buildingNode)));
            }
        });
    }

    private static java.util.function.Supplier<? extends TribeCard> buildCharacterFactory(
            TribeCardType type,
            JsonNode cardNode
    ) {
        int id = requiredInt(cardNode, "id");
        Era era = Era.valueOf(requiredText(cardNode, "era"));
        JsonNode modelNode = optionalObject(cardNode, "model");

        return switch (type) {
            case HUNTER -> {
                boolean hasEffect = requiredBoolean(requireObject(modelNode, "model"), "hasEffect");
                yield () -> new Hunter(id, era, hasEffect, type);
            }
            case BUILDER -> {
                JsonNode requiredModel = requireObject(modelNode, "model");
                int priceDiscount = requiredInt(requiredModel, "priceDiscount");
                int prestigePoints = requiredInt(requiredModel, "prestigePoints");
                yield () -> new Builder(id, era, type, priceDiscount, prestigePoints);
            }
            case GATHERER -> () -> new Gatherer(id, era, type);
            case ARTIST -> () -> new Artist(id, era, type);
            case INVENTOR -> {
                InventionType inventionType = InventionType.valueOf(
                        requiredText(requireObject(modelNode, "model"), "inventionType")
                );
                yield () -> new Inventor(id, era, type, inventionType);
            }
            case SHAMAN -> {
                int stars = requiredInt(requireObject(modelNode, "model"), "stars");
                yield () -> new Shaman(id, era, stars, type);
            }
            case EVENT -> throw new IllegalArgumentException("Character card " + id + " cannot have type EVENT");
        };
    }

    private static java.util.function.Supplier<? extends TribeCard> buildEventFactory(String family, JsonNode eventNode) {
        int id = requiredInt(eventNode, "id");
        Era era = Era.valueOf(requiredText(eventNode, "era"));
        JsonNode modelNode = required(eventNode, "model");

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

    private static java.util.function.Supplier<? extends BuildingCard> buildBuildingFactory(
            BenefitType benefitType,
            JsonNode buildingNode
    ) {
        int id = requiredInt(buildingNode, "id");
        Era era = Era.valueOf(requiredText(buildingNode, "era"));
        JsonNode modelNode = required(buildingNode, "model");
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
            case EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT,
                 EVENT_BONUS_SHAMAN_WIN_MULTIPLIER,
                 END_ROUND_EXTRA_CARD,
                 END_ROUND_TOTEM_POSITION,
                 FINAL_PRESTIGE_POINTS_FOR_SET,
                 FINAL_BUILDER_TOTAL_PP_MULTIPLIER,
                 FINAL_BONUS_MULTIPLIER_FOR_TYPE,
                 FINAL_BONUS_BIG_PP_REWARD -> () -> new BuildingCard(id, era, foodCost, prestigePoints, benefitType);
        };
    }

    private static void appendGroupedRecords(
            JsonNode groupedRecordsNode,
            String groupName,
            Consumer<Map.Entry<String, JsonNode>> entryConsumer
    ) {
        JsonNode groups = requireObject(groupedRecordsNode, "cards." + groupName);
        Iterator<Map.Entry<String, JsonNode>> entries = groups.fields();
        while (entries.hasNext()) {
            entryConsumer.accept(entries.next());
        }
    }

    private static JsonNode required(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            throw new IllegalArgumentException("Missing required field '" + fieldName + "'");
        }
        return child;
    }

    private static int requiredInt(JsonNode node, String fieldName) {
        return required(node, fieldName).asInt();
    }

    private static boolean requiredBoolean(JsonNode node, String fieldName) {
        return required(node, fieldName).asBoolean();
    }

    private static String requiredText(JsonNode node, String fieldName) {
        return required(node, fieldName).asText();
    }

    private static JsonNode requireObject(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing required object '" + fieldName + "'");
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Expected object for '" + fieldName + "'");
        }
        return node;
    }

    private static JsonNode requiredArray(JsonNode node, String fieldName) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Expected array for '" + fieldName + "'");
        }
        return node;
    }

    private static JsonNode optionalObject(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isObject()) {
            throw new IllegalArgumentException("Expected object for '" + fieldName + "'");
        }
        return child;
    }
}
