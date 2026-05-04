package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingTile;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
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
    void restoredBoardConstructor_ExposesSnapshotAndReturnsDeckCopies() {
        Gatherer upperTribe = new Gatherer(11, Era.first, TribeCardType.GATHERER);
        Gatherer lowerTribe = new Gatherer(12, Era.first, TribeCardType.GATHERER);
        Gatherer remainingTribe = new Gatherer(13, Era.second, TribeCardType.GATHERER);
        BuildingCard upperBuilding = new BuildingCard(14, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        BuildingCard lowerBuilding = new BuildingCard(15, Era.first, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard remainingBuilding = new BuildingCard(16, Era.second, 1, 1, BenefitType.END_ROUND_EXTRA_CARD);

        List<Player> restoredPlayers = players("Alice", "Bob", "Charlie", "Dave", "Eve");
        OfferPath restoredOfferPath = new OfferPath();
        restoredOfferPath.setup(restoredPlayers);
        restoredOfferPath.placePlayer(restoredPlayers.get(0), "B");

        Board restoredBoard = new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>(List.of(remainingTribe))),
                BuildingDeck.fromOrderedCards(new ArrayList<>(List.of(remainingBuilding))),
                restoredPlayers,
                new UpperPile(List.of(upperTribe), List.of(upperBuilding)),
                new LowerPile(List.of(lowerTribe), List.of(lowerBuilding)),
                restoredOfferPath
        );

        assertEquals(List.of(upperTribe, upperBuilding), restoredBoard.getUpperRowCards());
        assertEquals(List.of(lowerTribe, lowerBuilding), restoredBoard.getLowerRowCards());
        assertEquals(6, restoredBoard.getAvailableOfferSpaces().size(), "Con 5 giocatori e B occupato devono restare 6 spazi liberi");
        assertEquals(5, restoredBoard.getStartingBlocks().size(), "I blocchi iniziali ripristinati devono restare disponibili");

        List<Card> remainingTribes = restoredBoard.getRemainingTribeDeckCards();
        List<Card> remainingBuildings = restoredBoard.getRemainingBuildingDeckCards();
        remainingTribes.clear();
        remainingBuildings.clear();

        assertEquals(1, restoredBoard.getRemainingTribeDeckCards().size(), "Le copie del deck tribù non devono mutare lo stato interno");
        assertEquals(1, restoredBoard.getRemainingBuildingDeckCards().size(), "Le copie del deck edifici non devono mutare lo stato interno");
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
    void resetBoardAfterRound_WithoutEraChangeReturnsFalseAndKeepsBuildingsInLowerRow() {
        Gatherer lowerCard = new Gatherer(21, Era.first, TribeCardType.GATHERER);
        Gatherer upperCard = new Gatherer(22, Era.first, TribeCardType.GATHERER);
        Gatherer refillOne = new Gatherer(23, Era.first, TribeCardType.GATHERER);
        Gatherer refillTwo = new Gatherer(24, Era.first, TribeCardType.GATHERER);
        Gatherer refillThree = new Gatherer(25, Era.first, TribeCardType.GATHERER);
        BuildingCard lowerBuilding = new BuildingCard(26, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);

        OfferPath restoredOfferPath = new OfferPath(new StartingTile(), new ArrayList<OfferSpace>());
        Board board = new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>(List.of(refillOne, refillTwo, refillThree))),
                BuildingDeck.fromOrderedCards(new ArrayList<>()),
                players("Alice", "Bob"),
                new UpperPile(List.of(upperCard), List.of()),
                new LowerPile(List.of(lowerCard), List.of(lowerBuilding)),
                restoredOfferPath
        );

        boolean eraChanged = board.resetBoardAfterRound(Era.first);

        assertFalse(eraChanged, "Finché si pescano carte della stessa era non deve scattare il cambio era");
        assertTrue(board.getLowerRowCards().contains(upperCard), "Le carte tribù della riga alta devono scendere");
        assertTrue(board.getLowerRowCards().contains(lowerBuilding), "Gli edifici già nella riga bassa devono restare finché non cambia era");
    }

    @Test
    void getDraftableLowerCardsFiltersByActualCardDrawability() {
        Player poorPlayer = new Player("Alice", 1, 0, TotemColor.RED, new Tribe());
        Player richPlayer = new Player("Bob", 5, 0, TotemColor.BLUE, new Tribe());
        Gatherer lowerTribe = new Gatherer(31, Era.first, TribeCardType.GATHERER);
        BuildingCard expensiveLowerBuilding = new BuildingCard(32, Era.first, 4, 1, BenefitType.END_ROUND_EXTRA_CARD);

        Board board = restoredBoard(
                List.of(),
                List.of(),
                List.of(poorPlayer, richPlayer),
                new UpperPile(),
                new LowerPile(List.of(lowerTribe), List.of(expensiveLowerBuilding))
        );

        assertEquals(List.of(lowerTribe), board.getDraftableLowerCards(poorPlayer));
        assertEquals(List.of(lowerTribe, expensiveLowerBuilding), board.getDraftableLowerCards(richPlayer));
    }

    @Test
    void resolveLowerEventsDelegatesVisibleLowerEventsToLowerPile() {
        List<String> executionLog = new ArrayList<>();
        HuntEventCard regularEvent = recordingHunt(41, Era.first, "HUNT", executionLog);
        SustenanceEventCard sustenanceEvent = recordingSustenance(42, Era.first, "SUSTENANCE", executionLog);

        Board board = restoredBoard(
                List.of(),
                List.of(),
                players("Alice", "Bob"),
                new UpperPile(),
                new LowerPile(List.of(regularEvent, sustenanceEvent), List.of())
        );

        board.resolveLowerEvents();

        assertEquals(List.of("HUNT", "SUSTENANCE"), executionLog);
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

    @Test
    void resetBoardAfterRoundOnEraChangeMovesUpperBuildingsToLowerAndRefillsNewEraBuildings() {
        Gatherer lowerCard = new Gatherer(51, Era.first, TribeCardType.GATHERER);
        Gatherer upperCard = new Gatherer(52, Era.first, TribeCardType.GATHERER);
        Gatherer secondEraTrigger = new Gatherer(53, Era.second, TribeCardType.GATHERER);
        BuildingCard upperBuilding = new BuildingCard(54, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        BuildingCard newEraBuildingOne = new BuildingCard(55, Era.second, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard newEraBuildingTwo = new BuildingCard(56, Era.second, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);

        Board board = restoredBoard(
                List.of(secondEraTrigger),
                List.of(newEraBuildingOne, newEraBuildingTwo),
                players("Alice", "Bob"),
                new UpperPile(List.of(upperCard), List.of(upperBuilding)),
                new LowerPile(List.of(lowerCard), List.of())
        );

        boolean eraChanged = board.resetBoardAfterRound(Era.first);

        assertTrue(eraChanged);
        assertTrue(board.getLowerRowCards().contains(upperCard));
        assertTrue(board.getLowerRowCards().contains(upperBuilding));
        assertTrue(board.getUpperRowCards().contains(secondEraTrigger));
        assertTrue(board.getUpperRowCards().contains(newEraBuildingOne));
        assertTrue(board.getUpperRowCards().contains(newEraBuildingTwo));
    }

    @Test
    void resetBoardAfterRoundRefillsThirdEraBuildingCountsForDifferentPlayerCounts() {
        BuildingCard twoPlayerOne = new BuildingCard(81, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard twoPlayerTwo = new BuildingCard(82, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard twoPlayerThree = new BuildingCard(83, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Gatherer secondThirdEraCard = new Gatherer(84, Era.third, TribeCardType.GATHERER);
        Board twoPlayerBoard = restoredBoard(
                List.of(new Gatherer(80, Era.third, TribeCardType.GATHERER), secondThirdEraCard),
                List.of(twoPlayerOne, twoPlayerTwo, twoPlayerThree),
                players("Alice", "Bob"),
                new UpperPile(),
                new LowerPile()
        );

        assertTrue(twoPlayerBoard.resetBoardAfterRound(Era.second));
        assertTrue(twoPlayerBoard.getUpperRowCards().contains(secondThirdEraCard));
        assertTrue(twoPlayerBoard.getUpperRowCards().containsAll(List.of(twoPlayerOne, twoPlayerTwo, twoPlayerThree)));

        BuildingCard fourPlayerOne = new BuildingCard(91, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard fourPlayerTwo = new BuildingCard(92, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard fourPlayerThree = new BuildingCard(93, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard fourPlayerFour = new BuildingCard(94, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Board fourPlayerBoard = restoredBoard(
                List.of(new Gatherer(90, Era.third, TribeCardType.GATHERER)),
                List.of(fourPlayerOne, fourPlayerTwo, fourPlayerThree, fourPlayerFour),
                players("Alice", "Bob", "Carol", "Dave"),
                new UpperPile(),
                new LowerPile()
        );

        assertTrue(fourPlayerBoard.resetBoardAfterRound(Era.second));
        assertTrue(fourPlayerBoard.getUpperRowCards().containsAll(List.of(fourPlayerOne, fourPlayerTwo, fourPlayerThree, fourPlayerFour)));

        BuildingCard threePlayerOne = new BuildingCard(131, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard threePlayerTwo = new BuildingCard(132, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard threePlayerThree = new BuildingCard(133, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        BuildingCard threePlayerFour = new BuildingCard(134, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Board threePlayerBoard = restoredBoard(
                List.of(new Gatherer(130, Era.third, TribeCardType.GATHERER)),
                List.of(threePlayerOne, threePlayerTwo, threePlayerThree, threePlayerFour),
                players("Alice", "Bob", "Carol"),
                new UpperPile(),
                new LowerPile()
        );

        assertTrue(threePlayerBoard.resetBoardAfterRound(Era.second));
        assertTrue(threePlayerBoard.getUpperRowCards().containsAll(List.of(threePlayerOne, threePlayerTwo, threePlayerThree, threePlayerFour)));
    }

    @Test
    void resetBoardAfterRoundRefillsFiveBuildingsForFivePlayersInThirdEra() {
        List<Card> buildings = List.of(
                new BuildingCard(101, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD),
                new BuildingCard(102, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD),
                new BuildingCard(103, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD),
                new BuildingCard(104, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD),
                new BuildingCard(105, Era.third, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD)
        );
        Board board = restoredBoard(
                List.of(new Gatherer(100, Era.third, TribeCardType.GATHERER)),
                buildings,
                players("Alice", "Bob", "Carol", "Dave", "Eve"),
                new UpperPile(),
                new LowerPile()
        );

        assertTrue(board.resetBoardAfterRound(Era.second));
        assertTrue(board.getUpperRowCards().containsAll(buildings));
    }

    @Test
    void resetBoardAfterRoundUsesZeroBuildingRefillForUnexpectedEraRegression() {
        Gatherer firstEraCard = new Gatherer(121, Era.first, TribeCardType.GATHERER);
        BuildingCard buildingThatShouldRemainInDeck = new BuildingCard(122, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        Board board = restoredBoard(
                List.of(firstEraCard),
                List.of(buildingThatShouldRemainInDeck),
                players("Alice", "Bob"),
                new UpperPile(),
                new LowerPile()
        );

        assertTrue(board.resetBoardAfterRound(Era.second));
        assertTrue(board.getUpperRowCards().contains(firstEraCard));
        assertTrue(board.getRemainingBuildingDeckCards().contains(buildingThatShouldRemainInDeck));
    }

    @Test
    void resolveAllEventsCollectsUpperAndLowerEventsWithSustenanceLast() {
        List<String> executionLog = new ArrayList<>();
        HuntEventCard upperEraThree = recordingHunt(61, Era.third, "REGULAR_ERA3", executionLog);
        SustenanceEventCard upperSustenance = recordingSustenance(62, Era.first, "SUST_ERA1", executionLog);
        HuntEventCard lowerEraOne = recordingHunt(63, Era.first, "REGULAR_ERA1", executionLog);
        SustenanceEventCard lowerSustenance = recordingSustenance(64, Era.third, "SUST_ERA3", executionLog);

        Board board = restoredBoard(
                List.of(),
                List.of(),
                players("Alice", "Bob"),
                new UpperPile(List.of(upperEraThree, upperSustenance), List.of()),
                new LowerPile(List.of(lowerEraOne, lowerSustenance), List.of())
        );

        board.resolveAllEvents();

        assertEquals(List.of("REGULAR_ERA1", "REGULAR_ERA3", "SUST_ERA1", "SUST_ERA3"), executionLog);
    }

    @Test
    void findCardByIdSearchesUpperThenLowerAndReturnsNullWhenMissing() {
        Gatherer upperCard = new Gatherer(71, Era.first, TribeCardType.GATHERER);
        Gatherer lowerCard = new Gatherer(72, Era.first, TribeCardType.GATHERER);

        Board board = restoredBoard(
                List.of(),
                List.of(),
                players("Alice", "Bob"),
                new UpperPile(List.of(upperCard), List.of()),
                new LowerPile(List.of(lowerCard), List.of())
        );

        assertSame(upperCard, board.findCardById(71));
        assertSame(lowerCard, board.findCardById(72));
        assertNull(board.findCardById(999));
    }

    private static Board restoredBoard(List<Card> tribeDeckCards,
                                       List<Card> buildingDeckCards,
                                       List<Player> players,
                                       UpperPile upperPile,
                                       LowerPile lowerPile) {
        return new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>(tribeDeckCards)),
                BuildingDeck.fromOrderedCards(new ArrayList<>(buildingDeckCards)),
                players,
                upperPile,
                lowerPile,
                new OfferPath(new StartingTile(), new ArrayList<OfferSpace>())
        );
    }

    private static HuntEventCard recordingHunt(int id, Era era, String label, List<String> executionLog) {
        return new HuntEventCard(id, era, TribeCardType.EVENT, 0, 0) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add(label);
            }
        };
    }

    private static SustenanceEventCard recordingSustenance(int id, Era era, String label, List<String> executionLog) {
        return new SustenanceEventCard(id, era, TribeCardType.EVENT, 0) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add(label);
            }
        };
    }
}
