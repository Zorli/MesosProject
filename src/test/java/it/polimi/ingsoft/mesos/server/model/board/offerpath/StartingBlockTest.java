package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the immediate effect of a single starting block. The checks focus on
 * visible player resources: food and prestige points.
 */
class StartingBlockTest {
    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player("Tester", 1, 10, TotemColor.RED, null);
    }

    /**
     * A positive food modifier is applied directly to the occupying player.
     */
    @Test
    void resolveEffect_PositiveFood_GainsFood() {
        StartingBlock block = new StartingBlock(1, 2, 0, player);
        block.resolveEffect();

        assertEquals(3, player.getFood(), "Player should have 1+2=3 food");
    }

    /**
     * If the player can pay a negative food modifier, food decreases and
     * prestige remains unchanged.
     */
    @Test
    void resolveEffect_NegativeFood_CanAfford_PaysFood() {
        StartingBlock block = new StartingBlock(1, -1, -5, player);
        block.resolveEffect();

        assertEquals(0, player.getFood(), "Player should have paid 1 food");
        assertEquals(10, player.getPrestigePoints(), "Prestige should not change");
    }

    /**
     * If the player cannot pay the food cost, food remains unchanged and the
     * prestige penalty is applied instead.
     */
    @Test
    void resolveEffect_NegativeFood_CannotAfford_LosesPrestige() {
        StartingBlock block = new StartingBlock(1, -5, -2, player);
        block.resolveEffect();

        assertEquals(1, player.getFood(), "Food should not change when the player cannot afford to pay");
        assertEquals(8, player.getPrestigePoints(), "Player should have lost 2 prestige points");
    }

    /**
     * Empty starting blocks are inert: resolving the block must not change any
     * player state because no player currently occupies it.
     */
    @Test
    void resolveEffectOnEmptyBlockDoesNothing() {
        StartingBlock block = new StartingBlock(1, 2, 0, null);

        block.resolveEffect();

        assertEquals(1, player.getFood());
        assertEquals(10, player.getPrestigePoints());
    }
}
