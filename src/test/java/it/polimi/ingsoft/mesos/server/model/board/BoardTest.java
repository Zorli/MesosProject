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
import it.polimi.ingsoft.mesos.server.model.game.cards.events.SustenanceEventCard;
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

/**
 * Tests the board as the public game surface for visible cards and offer-path
 * delegation. The tests assert observable behavior: card rows, draftability,
 * round reset, event resolution, and next-round placement effects.
 */
class BoardTest {

    /**
     * The board must report whether a visible card is in the lower row, upper
     * row, or absent from the board.
     */
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

    /**
     * Upper-row draftability includes building discounts already owned by the
     * player, so a discounted building can be drawn even with low food.
     */
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

    /**
     * Resetting the board discards the old lower row, moves upper cards down,
     * refills the upper row, and reports an era change when the refill crosses
     * into the next era.
     */
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

    /**
     * Drafted cards are removed from their visible row and no longer appear in
     * board row lookup.
     */
    @Test
    void cardDrafting_RemovesCardFromBoard_AndUpdatesCardRow() {
        Gatherer lowerCard = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        BuildingCard upperBuilding = new BuildingCard(2, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);

        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(lowerCard))),
                new BuildingDeck(new ArrayList<>(List.of(upperBuilding))),
                players("Alice", "Bob")
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

    /**
     * A player can only draw a card that is currently visible on the board.
     */
    @Test
    void canPlayerDrawCard_EdgeCase_ReturnsFalseIfCardNotOnBoard() {
        Player alice = players("Alice").get(0);
        Gatherer boardCard = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer missingCard = new Gatherer(2, Era.first, TribeCardType.GATHERER);

        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(boardCard))),
                new BuildingDeck(new ArrayList<>()),
                List.of(alice)
        );
        board.setupInitialRows();

        assertTrue(board.canPlayerDrawCard(alice, boardCard), "Alice dovrebbe poter pescare una carta base presente sulla plancia");

        assertFalse(board.canPlayerDrawCard(alice, missingCard), "Non si può pescare una carta inesistente sulla plancia, anche se si hanno i requisiti");
    }

    /**
     * Board methods delegate to the offer path for placement availability and
     * for the draft action associated with an occupied space.
     */
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

    /**
     * Event A and card-draft resolution order must stay consistent through the
     * board-level delegation API.
     */
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

    /**
     * Returning a player to the starting tile frees their offer space and
     * appends them to the next-round order.
     */
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

    /**
     * The board exposes only currently free offer spaces. After a player takes
     * a space, that same space must disappear from the available choices shown
     * to clients.
     */
    @Test
    void getAvailableOfferSpacesContainsOnlyFreeSpacesForTheCurrentPlayerCount() {
        List<Player> dummyPlayers = players("Alice", "Bob", "Charlie");
        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>()),
                dummyPlayers
        );
        board.buildOfferPath();

        assertEquals(List.of("B", "C", "D", "E", "F"), board.getAvailableOfferSpaces().stream().map(space -> space.getCode()).toList());

        board.placePlayerOnOfferSpace(dummyPlayers.get(0), "B");

        assertFalse(board.getAvailableOfferSpaces().stream().map(space -> space.getCode()).toList().contains("B"));
    }

    /**
     * Moving a disconnected player to the last starting block frees their
     * offer space and keeps them in the next-round order.
     */
    @Test
    void movePlayerToLastStartingTileFreesTheOfferSpace() {
        List<Player> dummyPlayers = players("Alice", "Bob", "Charlie");
        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>()),
                dummyPlayers
        );
        board.buildOfferPath();
        Player alice = dummyPlayers.getFirst();
        board.placePlayerOnOfferSpace(alice, "C");

        board.movePlayerToLastStartingTile(alice);

        assertTrue(board.isSpaceAvailable("C"));
        assertTrue(board.getNextRoundTurnOrder().contains(alice));
    }

    /**
     * Players still on the starting tile at the end of totem placement are
     * shifted to the back before card drafting starts.
     */
    @Test
    void shiftToLastStartingTileKeepsUnplacedPlayersOnTheStartingTile() {
        List<Player> dummyPlayers = players("Alice", "Bob", "Charlie");
        Board board = new Board(
                new TribeDeck(new ArrayList<>()),
                new BuildingDeck(new ArrayList<>()),
                dummyPlayers
        );
        board.buildOfferPath();
        board.placePlayerOnOfferSpace(dummyPlayers.getFirst(), "B");

        board.shiftToLastStartingTile();

        assertEquals(List.of(dummyPlayers.get(1), dummyPlayers.get(2)), board.getNextRoundTurnOrder());
    }

    /**
     * Lower-row draftability is filtered by the card rules, exactly like the
     * upper row used by normal card selection.
     */
    @Test
    void getDraftableLowerCardsReturnsOnlyDrawableLowerRowCards() {
        Gatherer lowerOne = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer lowerTwo = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer lowerThree = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        Player alice = players("Alice", "Bob").getFirst();
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(lowerOne, lowerTwo, lowerThree))),
                new BuildingDeck(new ArrayList<>()),
                players("Alice", "Bob")
        );
        board.setupInitialRows();
        board.removeLowerCard(lowerTwo);

        assertEquals(2, board.getDraftableLowerCards(alice).size());
        assertTrue(board.getDraftableLowerCards(alice).contains(lowerOne));
        assertTrue(board.getDraftableLowerCards(alice).contains(lowerThree));
        assertFalse(board.getDraftableLowerCards(alice).contains(lowerTwo));
    }

    /**
     * Board-level lower event resolution delegates the visible lower-row events
     * to the same event ordering rules used by the lower pile.
     */
    @Test
    void resolveLowerEventsAppliesEventsVisibleInTheLowerRow() {
        List<String> log = new ArrayList<>();
        Gatherer lowerOne = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer lowerTwo = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer lowerThree = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        HuntEventCard lowerEvent = recordingHunt(4, Era.first, log, "hunt");
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(lowerOne, lowerTwo, lowerThree, lowerEvent))),
                new BuildingDeck(new ArrayList<>()),
                players("Alice", "Bob")
        );
        board.setupInitialRows();
        board.resetBoardAfterRound(Era.first);

        board.resolveLowerEvents();

        assertEquals(List.of("hunt"), log);
    }

    /**
     * Card lookup is observable through the public id used by client commands:
     * the board must find both upper-row and lower-row cards, and return null
     * for ids that are not visible.
     */
    @Test
    void findCardByIdSearchesBothRowsAndReturnsNullWhenMissing() {
        Gatherer lowerOne = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer lowerTwo = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer lowerThree = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        HuntEventCard upperEvent = new HuntEventCard(4, Era.first, TribeCardType.EVENT, 1, 1);
        BuildingCard upperBuilding = new BuildingCard(5, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(lowerOne, lowerTwo, lowerThree, upperEvent))),
                new BuildingDeck(new ArrayList<>(List.of(upperBuilding))),
                players("Alice", "Bob")
        );
        board.setupInitialRows();

        assertSame(lowerTwo, board.findCardById(2));
        assertSame(upperEvent, board.findCardById(4));
        assertSame(upperBuilding, board.findCardById(5));
        assertNull(board.findCardById(999));
    }

    /**
     * All visible events are resolved by era, with sustenance events after the
     * other event types. The test uses tiny event doubles so it checks ordering
     * without depending on the concrete event effects.
     */
    @Test
    void resolveAllEventsResolvesRegularEventsBeforeSustenanceEventsInEraOrder() {
        List<String> log = new ArrayList<>();
        Gatherer lowerOne = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer lowerTwo = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer lowerThree = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        HuntEventCard huntSecond = recordingHunt(4, Era.second, log, "hunt-second");
        SustenanceEventCard sustenanceFirst = recordingSustenance(5, Era.first, log, "sustenance-first");
        HuntEventCard huntFirst = recordingHunt(6, Era.first, log, "hunt-first");
        SustenanceEventCard sustenanceThird = recordingSustenance(7, Era.third, log, "sustenance-third");
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(
                        lowerOne,
                        lowerTwo,
                        lowerThree,
                        huntSecond,
                        sustenanceFirst,
                        huntFirst,
                        sustenanceThird
                ))),
                new BuildingDeck(new ArrayList<>()),
                players("Alice", "Bob")
        );
        board.setupInitialRows();

        board.resolveAllEvents();

        assertEquals(List.of("hunt-first", "hunt-second", "sustenance-first", "sustenance-third"), log);
    }

    /**
     * When refill reaches a later era, upper building cards move down and the
     * board draws the number of new buildings required by player count and era.
     */
    @Test
    void resetBoardAfterRoundMovesBuildingsDownAndRefillsBuildingsOnEraChange() {
        Gatherer lowerOne = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer lowerTwo = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer lowerThree = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        Gatherer upperOne = new Gatherer(4, Era.first, TribeCardType.GATHERER);
        Gatherer upperTwo = new Gatherer(5, Era.first, TribeCardType.GATHERER);
        Gatherer upperThree = new Gatherer(6, Era.first, TribeCardType.GATHERER);
        Gatherer upperFour = new Gatherer(7, Era.first, TribeCardType.GATHERER);
        Gatherer upperFive = new Gatherer(8, Era.first, TribeCardType.GATHERER);
        Gatherer upperSix = new Gatherer(9, Era.first, TribeCardType.GATHERER);
        Gatherer eraTwoCard = new Gatherer(10, Era.second, TribeCardType.GATHERER);
        BuildingCard oldBuilding = new BuildingCard(20, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        BuildingCard newBuildingOne = new BuildingCard(21, Era.second, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard newBuildingTwo = new BuildingCard(22, Era.second, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(
                        lowerOne,
                        lowerTwo,
                        lowerThree,
                        upperOne,
                        upperTwo,
                        upperThree,
                        upperFour,
                        upperFive,
                        upperSix,
                        eraTwoCard
                ))),
                new BuildingDeck(new ArrayList<>(List.of(oldBuilding, newBuildingOne, newBuildingTwo))),
                players("Alice", "Bob")
        );
        board.setupInitialRows();

        boolean eraChanged = board.resetBoardAfterRound(Era.first);

        assertTrue(eraChanged);
        assertTrue(board.getLowerRowCards().contains(upperOne));
        assertEquals(CardRow.LOWER, board.getCardRow(oldBuilding));
        assertEquals(CardRow.UPPER, board.getCardRow(newBuildingOne));
        assertEquals(CardRow.UPPER, board.getCardRow(newBuildingTwo));
        assertEquals(CardRow.UPPER, board.getCardRow(eraTwoCard));
    }

    /**
     * Third-era refill draws the larger building batch required by the rules.
     * With two players, entering the third era must reveal three new buildings.
     */
    @Test
    void resetBoardAfterRoundRefillsThreeBuildingsWhenTwoPlayersReachThirdEra() {
        Gatherer lowerOne = new Gatherer(1, Era.second, TribeCardType.GATHERER);
        Gatherer lowerTwo = new Gatherer(2, Era.second, TribeCardType.GATHERER);
        Gatherer lowerThree = new Gatherer(3, Era.second, TribeCardType.GATHERER);
        Gatherer upperOne = new Gatherer(4, Era.second, TribeCardType.GATHERER);
        Gatherer upperTwo = new Gatherer(5, Era.second, TribeCardType.GATHERER);
        Gatherer upperThree = new Gatherer(6, Era.second, TribeCardType.GATHERER);
        Gatherer upperFour = new Gatherer(7, Era.second, TribeCardType.GATHERER);
        Gatherer upperFive = new Gatherer(8, Era.second, TribeCardType.GATHERER);
        Gatherer upperSix = new Gatherer(9, Era.second, TribeCardType.GATHERER);
        Gatherer firstThirdEraCard = new Gatherer(10, Era.third, TribeCardType.GATHERER);
        BuildingCard oldBuilding = new BuildingCard(30, Era.second, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        BuildingCard newBuildingOne = new BuildingCard(31, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard newBuildingTwo = new BuildingCard(32, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard newBuildingThree = new BuildingCard(33, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(
                        lowerOne,
                        lowerTwo,
                        lowerThree,
                        upperOne,
                        upperTwo,
                        upperThree,
                        upperFour,
                        upperFive,
                        upperSix,
                        firstThirdEraCard
                ))),
                new BuildingDeck(new ArrayList<>(List.of(oldBuilding, newBuildingOne, newBuildingTwo, newBuildingThree))),
                players("Alice", "Bob")
        );
        board.setupInitialRows();

        boolean eraChanged = board.resetBoardAfterRound(Era.second);

        assertTrue(eraChanged);
        assertEquals(CardRow.LOWER, board.getCardRow(oldBuilding));
        assertEquals(CardRow.UPPER, board.getCardRow(newBuildingOne));
        assertEquals(CardRow.UPPER, board.getCardRow(newBuildingTwo));
        assertEquals(CardRow.UPPER, board.getCardRow(newBuildingThree));
    }

    private static HuntEventCard recordingHunt(int id, Era era, List<String> log, String label) {
        return new HuntEventCard(id, era, TribeCardType.EVENT, 0, 0) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                log.add(label);
            }
        };
    }

    private static SustenanceEventCard recordingSustenance(int id, Era era, List<String> log, String label) {
        return new SustenanceEventCard(id, era, TribeCardType.EVENT, 0) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                log.add(label);
            }
        };
    }

}
