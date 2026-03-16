package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardNotDrawableExceptionTest {

    @Test
    void constructorBuildsMessageWithPlayerAndCard() {
        Player player = new Player("Alice", 0, 0, TotemColor.RED, null);
        BuildingCard card = new BuildingCard(7, Era.first, 3, 1, BenefitType.END_ROUND_EXTRA_CARD);

        CardNotDrawableException exception = new CardNotDrawableException(player, card);

        assertEquals("Card 7 is not drawable by player Alice", exception.getMessage());
    }
}
