package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StartingBlockTest {
    private Player player;
    @BeforeEach
    void setUp() {
        player = new Player("Tester", 1, 10, TotemColor.RED, null);
    }

    @Test
    void resolveEffect_PositiveFood_GainsFood() {
        StartingBlock block = new StartingBlock(1, 2, 0, player);
        block.resolveEffect();

        assertEquals(3, player.getFood(), "Il giocatore dovrebbe avere 1+2=3 cibo");
    }

    @Test
    void resolveEffect_NegativeFood_CanAfford_PaysFood() {
        StartingBlock block = new StartingBlock(1, -1, -5, player);
        block.resolveEffect();

        assertEquals(0, player.getFood(), "Il giocatore dovrebbe aver pagato 1 cibo");
        assertEquals(10, player.getPrestigePoints(), "Il prestigio non dovrebbe cambiare");
    }

    @Test
    void resolveEffect_NegativeFood_CannotAfford_LosesPrestige() {
        StartingBlock block = new StartingBlock(1, -5, -2, player);
        block.resolveEffect();

        assertEquals(1, player.getFood(), "Il cibo non deve cambiare se il giocatore non può pagare");
        assertEquals(8, player.getPrestigePoints(), "Il giocatore dovrebbe aver perso 2 punti prestigio");
    }
}