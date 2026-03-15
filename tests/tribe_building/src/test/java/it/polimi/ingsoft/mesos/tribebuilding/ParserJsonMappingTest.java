package it.polimi.ingsoft.mesos.tribebuilding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.model.Factory.DeckTemplate;
import it.polimi.ingsoft.mesos.server.model.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.HunterBonus;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.ShamanStarsBonus;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserJsonMappingTest {
    private static final Path DECK_SETUP_PATH = Path.of(
            "..", "..", "src", "main", "resources", "it", "polimi", "ingsoft", "mesos",
            "server", "model", "Factory", "deckSetup.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseTribeDeckMapsCharactersAndEventsIntoConcreteClasses() {
        Map<Integer, TribeCard> cardsById = drainTribeDeck(ParserJson.getDeckTemplate().createTribeDeck());

        assertEquals(96, cardsById.size(), "Expected 84 character cards and 12 event cards");

        Hunter hunter = assertInstanceOf(Hunter.class, cardsById.get(1));
        assertTrue(hunter.getHasEffect(), "Hunter #1 should keep its immediate effect flag");

        Builder builder = assertInstanceOf(Builder.class, cardsById.get(6));
        assertEquals(1, builder.getPriceDiscount());
        assertEquals(3, builder.getPrestigePoints());

        Inventor inventor = assertInstanceOf(Inventor.class, cardsById.get(73));
        assertEquals(InventionType.NECKLACE, inventor.getInventionType());

        Shaman shaman = assertInstanceOf(Shaman.class, cardsById.get(81));
        assertEquals(3, shaman.getStars());

        HuntEventCard huntEvent = assertInstanceOf(HuntEventCard.class, cardsById.get(85));
        assertEquals(1, huntEvent.getFoodReward());
        assertEquals(1, huntEvent.getPrestigePointsReward());

        SustenanceEventCard sustenanceEvent = assertInstanceOf(SustenanceEventCard.class, cardsById.get(95));
        assertEquals(3, sustenanceEvent.getPrestigePointsToRemovePerCharacter());
    }

    @Test
    void parserJsonExposesASingleSharedDeckTemplateInstance() {
        DeckTemplate firstTemplate = ParserJson.getDeckTemplate();
        DeckTemplate secondTemplate = ParserJson.getDeckTemplate();

        assertSame(firstTemplate, secondTemplate);
        assertEquals(96, firstTemplate.getTribeCardTemplateCount());
        assertEquals(21, firstTemplate.getBuildingCardTemplateCount());
    }

    @Test
    void deckTemplateReturnsTheCorrectTribeDeckForEachSupportedPlayerCount() throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(DECK_SETUP_PATH));
        JsonNode characters = root.get("cards").get("characters");
        int eventCount = countCards(root.get("cards").get("events"));
        DeckTemplate deckTemplate = ParserJson.getDeckTemplate();

        for (int playerCount = 2; playerCount <= 5; playerCount++) {
            int allowedCharacters = 0;
            Iterator<JsonNode> characterCards = iterateGroupedCards(characters);
            while (characterCards.hasNext()) {
                JsonNode character = characterCards.next();
                JsonNode minPlayersNode = character.get("minPlayers");
                if (minPlayersNode == null || minPlayersNode.isNull() || minPlayersNode.asInt() <= playerCount) {
                    allowedCharacters++;
                }
            }

            int expectedTotal = allowedCharacters + eventCount;
            Map<Integer, TribeCard> cardsById = drainTribeDeck(deckTemplate.createTribeDeck(playerCount));

            assertEquals(expectedTotal, cardsById.size(), "Unexpected tribe deck size for " + playerCount + " players");
            assertTrue(cardsById.containsKey(1), "Cards available at all player counts must stay present");
        }
        assertFalse(drainTribeDeck(deckTemplate.createTribeDeck(2)).containsKey(4));
        assertTrue(drainTribeDeck(deckTemplate.createTribeDeck(3)).containsKey(4));
    }

    @Test
    void deckTemplateCreatesFreshCardInstancesForEachNewDeck() {
        DeckTemplate deckTemplate = ParserJson.getDeckTemplate();

        Map<Integer, TribeCard> firstDeck = drainTribeDeck(deckTemplate.createTribeDeck(5));
        Map<Integer, TribeCard> secondDeck = drainTribeDeck(deckTemplate.createTribeDeck(5));

        assertNotSame(firstDeck.get(1), secondDeck.get(1));
        assertNotSame(firstDeck.get(73), secondDeck.get(73));

        Map<Integer, BuildingCard> firstBuildingDeck = drainBuildingDeck(deckTemplate.createBuildingDeck());
        Map<Integer, BuildingCard> secondBuildingDeck = drainBuildingDeck(deckTemplate.createBuildingDeck());

        assertNotSame(firstBuildingDeck.get(97), secondBuildingDeck.get(97));
        assertNotSame(firstBuildingDeck.get(106), secondBuildingDeck.get(106));
    }

    @Test
    void deckTemplateRejectsUnsupportedPlayerCounts() {
        DeckTemplate deckTemplate = ParserJson.getDeckTemplate();

        assertThrows(IllegalArgumentException.class, () -> deckTemplate.createTribeDeck(1));
        assertThrows(IllegalArgumentException.class, () -> deckTemplate.createTribeDeck(6));
    }

    @Test
    void parseBuildingDeckMapsConcreteAndGenericBenefits() {
        Map<Integer, BuildingCard> cardsById = drainBuildingDeck(ParserJson.getDeckTemplate().createBuildingDeck());

        assertEquals(21, cardsById.size(), "Expected 21 building cards in the deck");

        SetForFood setForFood = assertInstanceOf(SetForFood.class, cardsById.get(97));
        assertEquals(BenefitType.POST_DRAW_SET_FOR_FOOD, setForFood.getBenefitType());
        assertEquals(4, setForFood.getFoodCost());
        assertEquals(3, setForFood.getPrestigePoints());
        assertEquals(5, setForFood.getRewardFood());

        ShamanStarsBonus shamanStarsBonus = assertInstanceOf(ShamanStarsBonus.class, cardsById.get(104));
        assertEquals(BenefitType.EVENT_BONUS_STARS_SHAMAN, shamanStarsBonus.getBenefitType());
        assertEquals(3, shamanStarsBonus.getStarsBonus());

        HunterBonus hunterBonus = assertInstanceOf(HunterBonus.class, cardsById.get(106));
        assertEquals(1, hunterBonus.getBonusFoodPerHunter());
        assertEquals(1, hunterBonus.getBonusPrestigePointsPerHunter());

        BuildingCard genericFinalBuilding = cardsById.get(117);
        assertEquals(BenefitType.FINAL_BONUS_BIG_PP_REWARD, genericFinalBuilding.getBenefitType());
        assertEquals(10, genericFinalBuilding.getFoodCost());
        assertEquals(0, genericFinalBuilding.getPrestigePoints());
    }

    private Map<Integer, TribeCard> drainTribeDeck(TribeDeck deck) {
        Map<Integer, TribeCard> cardsById = new HashMap<>();
        while (!deck.isEmpty()) {
            TribeCard card = (TribeCard) deck.draw();
            cardsById.put(card.getId(), card);
        }
        return cardsById;
    }

    private Map<Integer, BuildingCard> drainBuildingDeck(BuildingDeck deck) {
        Map<Integer, BuildingCard> cardsById = new HashMap<>();
        while (!deck.isEmpty()) {
            BuildingCard card = (BuildingCard) deck.draw();
            cardsById.put(card.getId(), card);
        }
        return cardsById;
    }

    private int countCards(JsonNode groupedCards) {
        int total = 0;
        Iterator<Map.Entry<String, JsonNode>> groups = groupedCards.fields();
        while (groups.hasNext()) {
            total += groups.next().getValue().size();
        }
        return total;
    }

    private Iterator<JsonNode> iterateGroupedCards(JsonNode groupedCards) {
        List<JsonNode> cards = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> groups = groupedCards.fields();
        while (groups.hasNext()) {
            for (JsonNode card : groups.next().getValue()) {
                cards.add(card);
            }
        }
        return cards.iterator();
    }
}
