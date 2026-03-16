package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    @Test
    void getCardRowReturnsUpperLowerOrNotPresent() {
        Gatherer lowerCard = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer secondLowerCard = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer thirdLowerCard = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        HuntEventCard upperCard = new HuntEventCard(4, Era.first, TribeCardType.EVENT, 1, 1);
        BuildingCard upperBuilding = new BuildingCard(5, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        Card missingCard = new BuildingCard(6, Era.first, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);

        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(lowerCard, secondLowerCard, thirdLowerCard, upperCard))),
                new BuildingDeck(new ArrayList<>(List.of(upperBuilding))),
                players("Alice", "Bob")
        );
        board.setupInitialRows();

        assertEquals(CardRow.LOWER, board.getCardRow(lowerCard));
        assertEquals(CardRow.UPPER, board.getCardRow(upperCard));
        assertEquals(CardRow.NOT_PRESENT, board.getCardRow(missingCard));
    }

    @Test
    void getDraftableUpperCardsUsesDiscountedBuildingCost() {
        BuildingCard discountedBuilding = new BuildingCard(5, Era.first, 3, 1, BenefitType.END_ROUND_EXTRA_CARD);
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(20, Era.first, TribeCardType.BUILDER, 2, 0));
        Player discountedPlayer = new Player("Alice", 1, 0, TotemColor.RED, tribe);
        Player secondPlayer = new Player("Bob", 10, 0, TotemColor.BLUE, new Tribe());

        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>(List.of(discountedBuilding))),
                List.of(discountedPlayer, secondPlayer)
        );
        board.setupInitialRows();

        assertTrue(board.getDraftableUpperCards(discountedPlayer).contains(discountedBuilding));
    }

    @Test
    void resetBoardAfterRoundShiftsCardsAndReturnsEraChangeFlag() {
        Gatherer discardedLowerOne = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer discardedLowerTwo = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer discardedLowerThree = new Gatherer(3, Era.first, TribeCardType.GATHERER);

        Gatherer shiftedUpperOne = new Gatherer(4, Era.first, TribeCardType.GATHERER);
        HuntEventCard shiftedUpperTwo = new HuntEventCard(5, Era.first, TribeCardType.EVENT, 1, 1);
        Gatherer shiftedUpperThree = new Gatherer(6, Era.first, TribeCardType.GATHERER);
        Gatherer shiftedUpperFour = new Gatherer(7, Era.first, TribeCardType.GATHERER);
        HuntEventCard shiftedUpperFive = new HuntEventCard(8, Era.first, TribeCardType.EVENT, 1, 1);
        Gatherer shiftedUpperSix = new Gatherer(9, Era.first, TribeCardType.GATHERER);
        Gatherer refilledUpperCard = new Gatherer(10, Era.second, TribeCardType.GATHERER);

        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(
                        discardedLowerOne,
                        discardedLowerTwo,
                        discardedLowerThree,
                        shiftedUpperOne,
                        shiftedUpperTwo,
                        shiftedUpperThree,
                        shiftedUpperFour,
                        shiftedUpperFive,
                        shiftedUpperSix,
                        refilledUpperCard
                ))),
                new BuildingDeck(new ArrayList<>()),
                players("Alice", "Bob")
        );
        board.setupInitialRows();

        boolean eraChanged = board.resetBoardAfterRound(Era.first);

        assertTrue(eraChanged);
        assertEquals(6, board.getLowerRowCards().size());
        assertTrue(board.getLowerRowCards().contains(shiftedUpperTwo));
        assertTrue(board.getLowerRowCards().contains(shiftedUpperFive));
        assertEquals(List.of(refilledUpperCard), board.getUpperRowCards());
    }

    private static List<Player> players(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] colors = TotemColor.values();

        for (int i = 0; i < nicknames.length; i++) {
            players.add(new Player(nicknames[i], 10, 0, colors[i], null));
        }

        return players;
    }
}
