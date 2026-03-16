package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardNotPresentExceptionTest {

    @Test
    void constructorBuildsMessageWithCardId() {
        BuildingCard card = new BuildingCard(7, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);

        CardNotPresentException exception = new CardNotPresentException(card);

        assertEquals("Requested card is not present on the board: 7", exception.getMessage());
    }

    @Test
    void constructorBuildsMessageForNullCard() {
        CardNotPresentException exception = new CardNotPresentException(null);

        assertEquals("Requested card is not present on the board: null", exception.getMessage());
    }
}
