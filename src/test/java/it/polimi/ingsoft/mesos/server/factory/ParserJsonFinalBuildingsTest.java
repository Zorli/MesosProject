package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.factory.ParserJson;
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ParserJsonFinalBuildingsTest {

    @Test
    void parsesRepresentativeCharacterAndEventCards() {
        assertInstanceOf(Hunter.class, ParserJson.getDeckTemplate().createCardById(1));
        assertInstanceOf(Builder.class, ParserJson.getDeckTemplate().createCardById(6));
        assertInstanceOf(Gatherer.class, ParserJson.getDeckTemplate().createCardById(10));
        assertInstanceOf(Artist.class, ParserJson.getDeckTemplate().createCardById(14));
        assertInstanceOf(Inventor.class, ParserJson.getDeckTemplate().createCardById(19));
        assertInstanceOf(Shaman.class, ParserJson.getDeckTemplate().createCardById(26));
        assertInstanceOf(HuntEventCard.class, ParserJson.getDeckTemplate().createCardById(85));
        assertInstanceOf(SustenanceEventCard.class, ParserJson.getDeckTemplate().createCardById(86));
        assertInstanceOf(ShamanRitualEventCard.class, ParserJson.getDeckTemplate().createCardById(87));
        assertInstanceOf(CavePaintingEventCard.class, ParserJson.getDeckTemplate().createCardById(88));
    }

    @Test
    void parsesRepresentativeBuildingCards() {
        assertInstanceOf(SetForFood.class, ParserJson.getDeckTemplate().createCardById(97));
        assertInstanceOf(SustenanceDiscount.class, ParserJson.getDeckTemplate().createCardById(98));
        assertInstanceOf(PairOfInventor.class, ParserJson.getDeckTemplate().createCardById(102));
        assertInstanceOf(ShamanStarsBonus.class, ParserJson.getDeckTemplate().createCardById(104));
        assertInstanceOf(HunterBonus.class, ParserJson.getDeckTemplate().createCardById(106));
        assertInstanceOf(CavePaintingBonus.class, ParserJson.getDeckTemplate().createCardById(108));
    }

    @Test
    void card109IsPrestigePointsForSet() {
        assertInstanceOf(PrestigePointsForSet.class, ParserJson.getDeckTemplate().createCardById(109));
    }

    @Test
    void card110IsMultiplierForType() {
        assertInstanceOf(MultiplierForType.class, ParserJson.getDeckTemplate().createCardById(110));
    }

    @Test
    void card117IsBigPpReward() {
        assertInstanceOf(BigPpReward.class, ParserJson.getDeckTemplate().createCardById(117));
    }
}
