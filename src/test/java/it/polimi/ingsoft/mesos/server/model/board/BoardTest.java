package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void cardDrafting_RemovesCardFromBoard_AndUpdatesCardRow() {
        // Setup base
        Gatherer lowerCard = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        BuildingCard upperBuilding = new BuildingCard(2, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);

        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(lowerCard))),
                new BuildingDeck(new ArrayList<>(List.of(upperBuilding))),
                players("Alice", "Bob") // Uso il metodo helper del tuo collega
        );
        board.setupInitialRows();

        assertEquals(CardRow.LOWER, board.getCardRow(lowerCard));
        assertEquals(CardRow.UPPER, board.getCardRow(upperBuilding));

        board.removeLowerCard(lowerCard);
        board.removeUpperCard(upperBuilding);

        assertEquals(CardRow.NOT_PRESENT, board.getCardRow(lowerCard), "La carta inferiore pescata non deve essere più sulla board");
        assertEquals(CardRow.NOT_PRESENT, board.getCardRow(upperBuilding), "L'edificio pescato non deve essere più sulla board");

        assertFalse(board.getLowerRowCards().contains(lowerCard));
        assertFalse(board.getUpperRowCards().contains(upperBuilding));
    }

    @Test
    void canPlayerDrawCard_EdgeCase_ReturnsFalseIfCardNotOnBoard() {
        Player alice = players("Alice").get(0);
        Gatherer boardCard = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer missingCard = new Gatherer(2, Era.first, TribeCardType.GATHERER); // Non la mettiamo nel deck!

        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(boardCard))),
                new BuildingDeck(new ArrayList<>()),
                List.of(alice)
        );
        board.setupInitialRows();

        assertTrue(board.canPlayerDrawCard(alice, boardCard), "Alice dovrebbe poter pescare una carta base presente sulla plancia");

        assertFalse(board.canPlayerDrawCard(alice, missingCard), "Non si può pescare una carta inesistente sulla plancia, anche se si hanno i requisiti");
    }

    @Test
    void offerPathDelegation_PlacePlayer_And_CheckAvailability() {
        List<Player> dummyPlayers = players("Alice", "Bob", "Charlie");
        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>()),
                dummyPlayers
        );

        board.buildOfferPath();

        Player alice = dummyPlayers.get(0);

        assertTrue(board.isSpaceAvailable("B"), "Lo spazio B deve essere libero all'inizio");

        board.placePlayerOnOfferSpace(alice, "B");

        assertFalse(board.isSpaceAvailable("B"), "Lo spazio B non deve più essere disponibile dopo averci piazzato Alice");

        int[] draftRules = board.returnNumberDrawableCard(alice);
        assertEquals(0, draftRules[0], "Lo spazio B dà 0 carte top");
        assertEquals(1, draftRules[1], "Lo spazio B dà 1 carta bottom");
    }

    @Test
    void resolveEventAAndResolutionOrder_DelegateToOfferPathConsistently() {
        List<Player> dummyPlayers = players("Alice", "Bob", "Charlie", "Dave", "Eve");
        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>()),
                dummyPlayers
        );
        board.buildOfferPath();

        Player alice = dummyPlayers.get(0);
        Player bob = dummyPlayers.get(1);
        Player charlie = dummyPlayers.get(2);

        board.placePlayerOnOfferSpace(alice, "A");
        board.placePlayerOnOfferSpace(bob, "D");
        board.placePlayerOnOfferSpace(charlie, "C");

        assertEquals(List.of(charlie, bob), board.getResolutionOrder(), "L'ordine di risoluzione deve ignorare A e seguire le posizioni");

        board.resolveEventA();

        assertTrue(board.isSpaceAvailable("A"), "Lo spazio A deve tornare libero dopo la risoluzione");
        assertTrue(board.getNextRoundTurnOrder().contains(alice), "Il giocatore su A deve tornare alla starting tile");
        assertArrayEquals(new int[]{0, 0}, board.returnNumberDrawableCard(alice), "Un giocatore già rientrato non deve avere più carte pescabili");
    }

    @Test
    void returnPlayerToStartingTileFreesOfferSpaceAndUpdatesNextRoundOrder() {
        List<Player> dummyPlayers = players("Alice", "Bob", "Charlie");
        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>()),
                dummyPlayers
        );
        board.buildOfferPath();
        Player alice = dummyPlayers.get(0);
        board.placePlayerOnOfferSpace(alice, "C");

        board.returnPlayerToStartingTile(alice);

        assertTrue(board.isSpaceAvailable("C"));
        assertEquals(alice, board.getNextRoundTurnOrder().get(0));
        assertEquals(3, board.getNextRoundTurnOrder().size());
    }

}
