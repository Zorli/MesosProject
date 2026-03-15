package it.polimi.ingsoft.mesos.tribebuilding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckSetupAssetCoverageTest {
    private static final Path DECK_SETUP_PATH = Path.of(
            "..", "..", "src", "main", "resources", "it", "polimi", "ingsoft", "mesos",
            "server", "model", "Factory", "deckSetup.json"
    );
    private static final Path CARD_ASSETS_DIR = Path.of(
            "..", "..", "src", "main", "resources", "it", "polimi", "ingsoft", "mesos",
            "client", "assets", "cards"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deckSetupContainsAContiguousIdRangeMatchingTheDeclaredIncludedPageCount() throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(DECK_SETUP_PATH));
        JsonNode cardsRoot = root.get("cards");
        int includedCardCount = root.get("source").get("includedCardCount").asInt();
        int totalCards =
                countCards(cardsRoot.get("characters"))
                        + countCards(cardsRoot.get("events"))
                        + countCards(cardsRoot.get("buildings"));

        assertEquals(
                includedCardCount,
                totalCards,
                "deckSetup.json must contain exactly the number of cards declared in source.includedCardCount"
        );

        Set<Integer> ids = new HashSet<>();
        collectIds(ids, cardsRoot.get("characters"));
        collectIds(ids, cardsRoot.get("events"));
        collectIds(ids, cardsRoot.get("buildings"));

        assertEquals(totalCards, ids.size(), "Card ids must be unique");
        for (int expectedId = 1; expectedId <= totalCards; expectedId++) {
            assertTrue(ids.contains(expectedId), "Missing card id " + expectedId);
        }
    }

    @Test
    void everyMappedCardHasACorrespondingAssetImage() throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(DECK_SETUP_PATH));
        JsonNode cardsRoot = root.get("cards");

        assertAssetsExist(cardsRoot.get("characters"));
        assertAssetsExist(cardsRoot.get("events"));
        assertAssetsExist(cardsRoot.get("buildings"));
    }

    private void collectIds(Set<Integer> ids, JsonNode cards) {
        Iterator<Map.Entry<String, JsonNode>> groups = cards.fields();
        while (groups.hasNext()) {
            Map.Entry<String, JsonNode> group = groups.next();
            for (JsonNode card : group.getValue()) {
                ids.add(card.get("id").asInt());
            }
        }
    }

    private void assertAssetsExist(JsonNode cards) {
        Iterator<Map.Entry<String, JsonNode>> groups = cards.fields();
        while (groups.hasNext()) {
            Map.Entry<String, JsonNode> group = groups.next();
            for (JsonNode card : group.getValue()) {
                int id = card.get("id").asInt();
                Path assetPath = CARD_ASSETS_DIR.resolve(id + ".jpg");
                assertTrue(Files.isRegularFile(assetPath), "Missing asset image for card id " + id);
            }
        }
    }

    private int countCards(JsonNode groupedCards) {
        int total = 0;
        Iterator<Map.Entry<String, JsonNode>> groups = groupedCards.fields();
        while (groups.hasNext()) {
            total += groups.next().getValue().size();
        }
        return total;
    }
}
