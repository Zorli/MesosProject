package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerTest {

    @Test
    void constructorCreatesFreshTribeWhenMissing() {
        Player player = new Player("Alice", 0, 0, TotemColor.RED);

        assertNotNull(player.getTribe());
    }

    @Test
    void overloadedConstructorKeepsProvidedTribeInstance() {
        Tribe tribe = new Tribe();

        Player player = new Player("Alice", 0, 0, TotemColor.RED, tribe);

        assertSame(tribe, player.getTribe());
    }

    @Test
    void gainFoodIncreasesCurrentFood() {
        Player player = new Player("Alice", 2, 0, TotemColor.BLUE);

        player.gainFood(4);

        assertEquals(6, player.getFood());
    }

    @Test
    void payFoodReducesFoodWhenEnough() {
        Player player = new Player("Alice", 6, 3, TotemColor.BLACK);

        player.payFood(2);

        assertEquals(4, player.getFood());
        assertEquals(3, player.getPrestigePoints());
    }

    @Test
    void payFoodConvertsMissingFoodToPrestigeLoss() {
        Player player = new Player("Alice", 2, 9, TotemColor.BLACK);

        player.payFood(5);

        assertEquals(0, player.getFood());
        assertEquals(6, player.getPrestigePoints());
    }

    @Test
    void payFoodUsesExactFoodWithoutConvertingPrestige() {
        Player player = new Player("Alice", 5, 4, TotemColor.BLACK);

        player.payFood(5);

        assertEquals(0, player.getFood());
        assertEquals(4, player.getPrestigePoints());
    }

    @Test
    void gainAndPayPrestigePointsChangeScore() {
        Player player = new Player("Alice", 0, 10, TotemColor.YELLOW);

        player.gainPrestigePoints(5);
        player.payPrestigePoints(4);

        assertEquals(11, player.getPrestigePoints());
    }

    @Test
    void initializeGameAttributesCreatesMissingTribe() throws Exception {
        Player player = new Player("Alice", 0, 0, TotemColor.RED);
        Field tribeField = Player.class.getDeclaredField("tribe");
        tribeField.setAccessible(true);
        tribeField.set(player, null);

        player.initializeGameAttributes();

        assertNotNull(player.getTribe());
    }

    @Test
    void initializeGameAttributesLeavesExistingTribeIntact() {
        Tribe existingTribe = new Tribe();
        Player player = new Player("Alice", 0, 0, TotemColor.BLACK, existingTribe);

        player.initializeGameAttributes();

        assertSame(existingTribe, player.getTribe());
    }

    @Test
    void getBuildingFoodDiscountComputesFromOwnedBuilders() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(10, Era.first, TribeCardType.BUILDER, 2, 3));
        tribe.addCharacter(new Builder(11, Era.first, TribeCardType.BUILDER, 4, 2));
        Player player = new Player("Alice", 0, 0, TotemColor.RED, tribe);

        assertEquals(6, player.getBuildingFoodDiscount());
    }

    @Test
    void getBuildingFoodDiscountFallsBackToZeroWhenNoDiscountBuilders() {
        Player player = new Player("Alice", 0, 0, TotemColor.RED, new Tribe());

        assertEquals(0, player.getBuildingFoodDiscount());
    }

    @Test
    void getBuildingFoodDiscountReturnsZeroForMissingTribe() throws Exception {
        Player player = new Player("Alice", 0, 0, TotemColor.RED);
        Field tribeField = Player.class.getDeclaredField("tribe");
        tribeField.setAccessible(true);
        tribeField.set(player, null);

        assertEquals(0, player.getBuildingFoodDiscount());
    }
}
