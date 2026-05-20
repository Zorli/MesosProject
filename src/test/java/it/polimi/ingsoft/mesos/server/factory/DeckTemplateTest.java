package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.factory.DeckTemplate;
import it.polimi.ingsoft.mesos.server.factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckTemplateTest {

    @Test
    void createTribeDeckRejectsUnsupportedPlayerCounts() {
        DeckTemplate template = ParserJson.getDeckTemplate();

        assertThrows(IllegalArgumentException.class, () -> template.createTribeDeck(1));
        assertThrows(IllegalArgumentException.class, () -> template.createTribeDeck(6));
    }

    @Test
    void createTribeDeckFiltersCardsByMinimumPlayerCount() {
        DeckTemplate template = ParserJson.getDeckTemplate();

        TribeDeck twoPlayerDeck = template.createTribeDeck(2);
        TribeDeck threePlayerDeck = template.createTribeDeck(3);
        TribeDeck fourPlayerDeck = template.createTribeDeck(4);
        TribeDeck fivePlayerDeck = template.createTribeDeck(5);

        assertEquals(64, twoPlayerDeck.getCardsInOrder().size());
        assertEquals(74, threePlayerDeck.getCardsInOrder().size());
        assertEquals(85, fourPlayerDeck.getCardsInOrder().size());
        assertEquals(96, fivePlayerDeck.getCardsInOrder().size());
        assertTrue(
                fivePlayerDeck.getCardsInOrder().stream()
                        .map(TribeCard.class::cast)
                        .filter(card -> card.getType() == TribeCardType.EVENT)
                        .count()
                        <= fivePlayerDeck.getCardsInOrder().size()
        );
    }

    @Test
    void createCardByIdReturnsFreshIndependentInstances() {
        DeckTemplate template = ParserJson.getDeckTemplate();

        Card firstInstance = template.createCardById(10);
        Card secondInstance = template.createCardById(10);

        assertEquals(firstInstance.getId(), secondInstance.getId());
        assertEquals(firstInstance.getClass(), secondInstance.getClass());
        assertNotSame(firstInstance, secondInstance);
    }

    @Test
    void createCardByIdRejectsUnknownIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> ParserJson.getDeckTemplate().createCardById(-1));
    }

    @Test
    void createBuildingDeckUsesAllConfiguredBlueprints() {
        DeckTemplate template = ParserJson.getDeckTemplate();

        BuildingDeck buildingDeck = template.createBuildingDeck();

        assertEquals(96, template.getTribeCardTemplateCount());
        assertEquals(21, template.getBuildingCardTemplateCount());
        assertEquals(template.getBuildingCardTemplateCount(), buildingDeck.getCardsInOrder().size());
        assertEquals(96, ParserJson.parseTribeDeck().getCardsInOrder().size());
        assertEquals(21, ParserJson.parseBuildingDeck().getCardsInOrder().size());
    }

    @Test
    void constructorCopiesBlueprintListsAndCreateTribeDeckUsesAllEntries() {
        ArrayList<DeckTemplate.TribeCardBlueprint> tribeBlueprints = new ArrayList<>(List.of(
                DeckTemplate.tribeCard(1, null, () -> new Gatherer(1, Era.first, TribeCardType.GATHERER)),
                DeckTemplate.tribeCard(2, 5, () -> new Gatherer(2, Era.first, TribeCardType.GATHERER))
        ));
        ArrayList<DeckTemplate.BuildingCardBlueprint> buildingBlueprints = new ArrayList<>(List.of(
                DeckTemplate.buildingCard(3, () -> new BuildingCard(3, Era.first, 1, 1, BenefitType.END_ROUND_EXTRA_CARD))
        ));

        DeckTemplate template = new DeckTemplate(tribeBlueprints, buildingBlueprints);
        tribeBlueprints.clear();
        buildingBlueprints.clear();

        TribeDeck tribeDeck = template.createTribeDeck();

        assertEquals(2, template.getTribeCardTemplateCount());
        assertEquals(1, template.getBuildingCardTemplateCount());
        assertEquals(2, tribeDeck.getCardsInOrder().size(), "Il deck completo deve usare tutti i blueprint senza filtrare per player count");
    }

    @Test
    void createCardByIdResolvesBuildingBlueprintsAfterTribeBlueprints() {
        DeckTemplate template = new DeckTemplate(
                List.of(DeckTemplate.tribeCard(10, null, () -> new Gatherer(10, Era.first, TribeCardType.GATHERER))),
                List.of(DeckTemplate.buildingCard(20, () -> new BuildingCard(20, Era.first, 2, 1, BenefitType.END_ROUND_EXTRA_CARD)))
        );

        Card card = template.createCardById(20);

        assertEquals(20, card.getId());
        assertTrue(card instanceof BuildingCard);
    }
}
