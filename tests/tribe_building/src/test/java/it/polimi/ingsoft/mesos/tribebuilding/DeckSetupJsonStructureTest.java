package it.polimi.ingsoft.mesos.tribebuilding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeckSetupJsonStructureTest {
    private static final Path DECK_SETUP_PATH = Path.of(
            "..", "..", "src", "main", "resources", "it", "polimi", "ingsoft", "mesos",
            "server", "model", "Factory", "deckSetup.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deckSetupJsonIsValidJson() throws IOException {
        String rawJson = Files.readString(DECK_SETUP_PATH);
        JsonNode root = assertDoesNotThrow(
                () -> objectMapper.readTree(rawJson),
                () -> "Invalid JSON in " + DECK_SETUP_PATH
        );

        assertNotNull(root);
    }

    @Test
    void deckSetupJsonHasExpectedTopLevelStructure() throws IOException {
        assumeTrue(deckSetupJsonIsParseable(), "JSON syntax must be fixed before validating structure");

        JsonNode root = objectMapper.readTree(Files.readString(DECK_SETUP_PATH));

        assertAll(
                () -> assertNotNull(root.get("schemaVersion"), "Missing top-level 'schemaVersion' field"),
                () -> assertNotNull(root.get("source"), "Missing top-level 'source' object"),
                () -> assertTrue(root.get("source").isObject(), "'source' must be a JSON object"),
                () -> assertNotNull(root.get("cards"), "Missing top-level 'cards' object"),
                () -> assertTrue(root.get("cards").isObject(), "'cards' must be a JSON object"),
                () -> assertNotNull(root.get("cards").get("characters"), "Missing 'cards.characters' object"),
                () -> assertTrue(root.get("cards").get("characters").isObject(), "'cards.characters' must be an object"),
                () -> assertNotNull(root.get("cards").get("events"), "Missing 'cards.events' object"),
                () -> assertTrue(root.get("cards").get("events").isObject(), "'cards.events' must be an object"),
                () -> assertNotNull(root.get("cards").get("buildings"), "Missing 'cards.buildings' object"),
                () -> assertTrue(root.get("cards").get("buildings").isObject(), "'cards.buildings' must be an object")
        );

        assertGroupedCards(root.get("cards").get("characters"), "cards.characters", "type");
        assertGroupedCards(root.get("cards").get("events"), "cards.events", "family");
        assertGroupedCards(root.get("cards").get("buildings"), "cards.buildings", "benefitType");
        assertCharactersUseOptionalFieldsConsistently(root.get("cards").get("characters"));
    }

    boolean deckSetupJsonIsParseable() {
        try {
            objectMapper.readTree(Files.readString(DECK_SETUP_PATH));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void assertGroupedCards(JsonNode groupedCards, String path, String redundantFieldName) {
        Iterator<Map.Entry<String, JsonNode>> entries = groupedCards.fields();
        assertTrue(entries.hasNext(), path + " must contain at least one subtype group");

        groupedCards.fields().forEachRemaining(entry -> {
            JsonNode cards = entry.getValue();
            assertTrue(cards.isArray(), path + "." + entry.getKey() + " must be an array");
            assertTrue(cards.size() > 0, path + "." + entry.getKey() + " must not be empty");
            for (JsonNode card : cards) {
                assertTrue(card.isObject(), path + "." + entry.getKey() + " entries must be objects");
                assertTrue(
                        !card.has(redundantFieldName),
                        path + "." + entry.getKey() + " entries must not repeat '" + redundantFieldName + "'"
                );
            }
        });
    }

    private void assertCharactersUseOptionalFieldsConsistently(JsonNode characterGroups) {
        characterGroups.fields().forEachRemaining(entry -> {
            for (JsonNode card : entry.getValue()) {
                if (card.has("minPlayers")) {
                    assertTrue(
                            !card.get("minPlayers").isNull(),
                            "cards.characters entries must omit 'minPlayers' instead of using null"
                    );
                }
                if (entry.getKey().equals("GATHERER") || entry.getKey().equals("ARTIST")) {
                    assertTrue(
                            !card.has("model"),
                            "cards.characters." + entry.getKey() + " entries must omit empty 'model' blocks"
                    );
                }
            }
        });
    }
}
