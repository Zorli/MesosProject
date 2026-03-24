package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import it.polimi.ingsoft.mesos.server.controller.player.Tribe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterCardBasicsTest {

    @Test
    void hunterApplyEffectRewardsFoodAndPrestigeWhenEnabled() {
        Tribe tribe = new Tribe();
        Player player = new Player("Alice", 0, 0, TotemColor.RED, tribe);
        tribe.addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER));
        tribe.addCharacter(new Hunter(2, Era.first, false, TribeCardType.HUNTER));

        Hunter hunter = new Hunter(3, Era.first, true, TribeCardType.HUNTER);
        tribe.addCharacter(hunter);
        hunter.applyEffect(player);

        assertTrue(hunter.getHasEffect());
        assertEquals(3, player.getFood());
        assertEquals(3, player.getPrestigePoints());
    }

    @Test
    void hunterApplyEffectDoesNothingWhenDisabled() {
        Tribe tribe = new Tribe();
        Player player = new Player("Alice", 1, 2, TotemColor.RED, tribe);
        tribe.addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER));

        Hunter hunter = new Hunter(2, Era.first, false, TribeCardType.HUNTER);
        hunter.applyEffect(player);

        assertFalse(hunter.getHasEffect());
        assertEquals(1, player.getFood());
        assertEquals(2, player.getPrestigePoints());
    }

    @Test
    void inventorAndCardBaseExposeMutableEraAndMetadata() {
        Inventor inventor = new Inventor(10, Era.first, TribeCardType.INVENTOR, InventionType.FIRE);

        inventor.setEra(Era.second);

        assertEquals(10, inventor.getId());
        assertEquals(Era.second, inventor.getEra());
        assertEquals(TribeCardType.INVENTOR, inventor.getType());
        assertEquals(InventionType.FIRE, inventor.getInventionType());
    }
}
