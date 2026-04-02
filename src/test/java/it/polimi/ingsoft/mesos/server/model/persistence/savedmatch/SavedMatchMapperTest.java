package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedMatchMapperTest {

    @Test
    void fromGameMapsBoardZonesOfferOccupancyTurnChoicesAndPlayerCards() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        Game game = MesosTestFixtures.createTwoPlayerGameplayFactory().createGame(players);
        game.setup();
        players.get(1).gainFood(10);

        game.moveTotem(players.get(0), "B");
        game.moveTotem(players.get(1), "C");
        game.chooseCard(players.get(0), MesosTestFixtures.findLowerRowCard(game, 10));
        game.chooseCard(players.get(1), MesosTestFixtures.findUpperRowCard(game, 97));

        SetForFood setForFood = (SetForFood) players.get(1).getTribe().getBuildings().getFirst();
        setForFood.restoreRuntimeState(Map.of(
                "HUNTER", 1,
                "BUILDER", 2,
                "GATHERER", 0,
                "ARTIST", 1,
                "INVENTOR", 0,
                "SHAMAN", 3
        ));

        SavedMatchAggregate aggregate = SavedMatchMapper.fromGame(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);

        assertEquals(String.valueOf(game.getId()), aggregate.gameId());
        assertEquals("Alice", aggregate.hostNickname());
        assertEquals(players.size(), aggregate.playerCount());
        assertEquals(SavedMatchStatus.IN_PROGRESS, aggregate.status());
        assertEquals(game.getCurrentEra(), aggregate.currentEra());
        assertEquals(game.getStatus(), aggregate.gameStatus());
        assertEquals(game.getTurnOrderTrack().getRoundNumber(), aggregate.roundNumber());
        assertEquals(game.getTurnOrderTrack().getCurrentRoundPhase(), aggregate.roundPhase());
        assertEquals(game.getTurnState().getPhase(), aggregate.turnPhase());
        assertEquals(
                game.getTurnState().isWaitingForCardSelection() ? game.getTurnOrderTrack().getCurrentPlayer().getNickname() : null,
                aggregate.activePlayerNickname()
        );
        assertEquals(game.getTurnOrderTrack().getCurrentPlayerIndex(), aggregate.currentPlayerIndex());

        assertIterableEquals(expectedPlayerRows(game), actualPlayerRows(aggregate));
        assertIterableEquals(expectedTurnOrderRows(game), actualTurnOrderRows(aggregate));
        assertIterableEquals(expectedTurnChoiceRows(game), actualTurnChoiceRows(aggregate));
        assertIterableEquals(expectedOfferSpaceRows(game), actualOfferSpaceRows(aggregate));
        assertIterableEquals(expectedStartingBlockRows(game), actualStartingBlockRows(aggregate));
        assertIterableEquals(expectedBoardCardRows(game), actualBoardCardRows(aggregate));
        assertIterableEquals(expectedPlayerCardRows(game), actualPlayerCardRows(aggregate));
        assertIterableEquals(expectedPlayerRuntimeRows(game), actualPlayerRuntimeRows(aggregate));
    }

    @Test
    void fromGameMarksFinishedMatchesAsCompletedAndRejectsDuplicatePlayerNicknames() {
        Game finishedGame = MesosTestFixtures.createTwoPlayerGameplayFactory()
                .createGame(MesosTestFixtures.createPlayers("Alice", "Bob"));
        finishedGame.setup();
        finishedGame.getTurnOrderTrack().setCurrentRoundPhase(RoundPhase.FINAL_SCORING);
        finishedGame.setStatus(GameStatus.FINISHED);

        SavedMatchAggregate finishedAggregate = SavedMatchMapper.fromGame(
                finishedGame,
                "Alice",
                SavedMatchCheckpointType.TURN_STARTED
        );

        assertEquals(SavedMatchStatus.COMPLETED, finishedAggregate.status());
        assertEquals(GameStatus.FINISHED, finishedAggregate.gameStatus());

        ArrayList<Player> duplicatePlayers = new ArrayList<>();
        duplicatePlayers.add(new Player("Alice", 0, 0, TotemColor.RED, null));
        duplicatePlayers.add(new Player("Alice", 0, 0, TotemColor.BLUE, null));
        Game duplicateNicknameGame = DefaultGameFactory.getInstance().createGame(duplicatePlayers);
        duplicateNicknameGame.setup();

        assertThrows(
                SavedMatchSnapshotConsistencyException.class,
                () -> SavedMatchMapper.fromGame(duplicateNicknameGame, "Alice", SavedMatchCheckpointType.GAME_INITIALIZED)
        );
    }

    @Test
    void fromGamePreservesOwnedCardPositionsAndNonEmptyLowerBuildingAndDeckZones() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob", "Carol");

        List<Integer> ownedCharacterIds = pickCharacterIds(3, Set.of(), 2);
        Set<Integer> usedTribeIds = Set.copyOf(ownedCharacterIds);
        List<Integer> visibleAndDeckTribeIds = pickTribeIds(3, usedTribeIds, 6);

        List<Integer> ownedBuildingIds = pickBuildingIds(Set.of(), 2);
        Set<Integer> usedBuildingIds = Set.copyOf(ownedBuildingIds);
        List<Integer> visibleAndDeckBuildingIds = pickBuildingIds(usedBuildingIds, 3);

        Player alice = players.getFirst();
        Player bob = players.get(1);
        alice.getTribe().addCharacter(createCharacter(ownedCharacterIds.get(0)));
        alice.getTribe().addCharacter(createCharacter(ownedCharacterIds.get(1)));
        bob.getTribe().addBuilding(createBuilding(ownedBuildingIds.get(0)));
        bob.getTribe().addBuilding(createBuilding(ownedBuildingIds.get(1)));

        UpperPile upperPile = new UpperPile(
                List.of(
                        createTribe(visibleAndDeckTribeIds.get(0)),
                        createTribe(visibleAndDeckTribeIds.get(1))
                ),
                List.of(createBuilding(visibleAndDeckBuildingIds.get(0)))
        );
        LowerPile lowerPile = new LowerPile(
                List.of(
                        createTribe(visibleAndDeckTribeIds.get(2)),
                        createTribe(visibleAndDeckTribeIds.get(3))
                ),
                List.of(createBuilding(visibleAndDeckBuildingIds.get(1)))
        );
        TribeDeck tribeDeck = TribeDeck.fromOrderedCards(new ArrayList<>(List.of(
                createCard(visibleAndDeckTribeIds.get(4)),
                createCard(visibleAndDeckTribeIds.get(5))
        )));
        BuildingDeck buildingDeck = BuildingDeck.fromOrderedCards(new ArrayList<>(List.of(
                createCard(visibleAndDeckBuildingIds.get(2))
        )));

        OfferPath offerPath = new OfferPath();
        offerPath.setup(players);
        Board board = new Board(tribeDeck, buildingDeck, players, upperPile, lowerPile, offerPath);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(players, 1, 4, RoundPhase.PLAYER_DRAWS);
        TurnState turnState = new TurnState(
                TurnPhase.WAITING_FOR_CARD_SELECTION,
                Map.of(bob, 1),
                Map.of(bob, 2)
        );
        Game game = new Game(42, players, turnState, turnOrderTrack, board, Era.second, GameStatus.IN_PROGRESS);

        SavedMatchAggregate aggregate = SavedMatchMapper.fromGame(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);

        assertIterableEquals(expectedBoardCardRows(game), actualBoardCardRows(aggregate));
        assertIterableEquals(expectedPlayerCardRows(game), actualPlayerCardRows(aggregate));
    }

    private static List<String> expectedPlayerRows(Game game) {
        List<Player> players = game.getPlayers();
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            rows.add(index + ":" + player.getNickname() + ":" + player.getTotem() + ":" + player.getFood() + ":" + player.getPrestigePoints());
        }
        return rows;
    }

    private static List<String> actualPlayerRows(SavedMatchAggregate aggregate) {
        return aggregate.players().stream()
                .sorted(Comparator.comparingInt(SavedMatchPlayerRecord::playerIndex))
                .map(player -> player.playerIndex() + ":" + player.nickname() + ":" + player.totemColor() + ":" + player.food() + ":" + player.prestigePoints())
                .toList();
    }

    private static List<String> expectedTurnOrderRows(Game game) {
        List<Player> turnOrder = game.getTurnOrderTrack().getTurnOrder();
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < turnOrder.size(); index++) {
            rows.add(index + ":" + turnOrder.get(index).getNickname());
        }
        return rows;
    }

    private static List<String> actualTurnOrderRows(SavedMatchAggregate aggregate) {
        return aggregate.turnOrder().stream()
                .sorted(Comparator.comparingInt(SavedMatchTurnOrderRecord::turnPosition))
                .map(record -> record.turnPosition() + ":" + record.nickname())
                .toList();
    }

    private static List<String> expectedTurnChoiceRows(Game game) {
        return game.getTurnState().getRemainingUpperChoicesByPlayer().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Player::getNickname)))
                .map(entry -> {
                    Player player = entry.getKey();
                    return player.getNickname()
                            + ":" + entry.getValue()
                            + ":" + game.getTurnState().getRemainingLowerChoices(player);
                })
                .toList();
    }

    private static List<String> actualTurnChoiceRows(SavedMatchAggregate aggregate) {
        return aggregate.turnChoices().stream()
                .sorted(Comparator.comparing(SavedMatchTurnChoiceRecord::nickname))
                .map(record -> record.nickname()
                        + ":" + record.remainingUpperChoices()
                        + ":" + record.remainingLowerChoices())
                .toList();
    }

    private static List<String> expectedOfferSpaceRows(Game game) {
        return game.getBoard().getOfferSpaces().stream()
                .sorted(Comparator.comparingInt(space -> space.getPositionIndex()))
                .map(space -> space.getCode()
                        + ":" + space.getPositionIndex()
                        + ":" + (space.getOccupiedBy() == null ? "-" : space.getOccupiedBy().getNickname()))
                .toList();
    }

    private static List<String> actualOfferSpaceRows(SavedMatchAggregate aggregate) {
        return aggregate.offerSpaces().stream()
                .sorted(Comparator.comparingInt(SavedMatchOfferSpaceRecord::positionIndex))
                .map(record -> record.offerSpaceCode()
                        + ":" + record.positionIndex()
                        + ":" + (record.occupiedByNickname() == null ? "-" : record.occupiedByNickname()))
                .toList();
    }

    private static List<String> expectedStartingBlockRows(Game game) {
        return game.getBoard().getStartingBlocks().stream()
                .sorted(Comparator.comparingInt(block -> block.getPosition()))
                .map(block -> block.getPosition()
                        + ":" + block.getFoodModifier()
                        + ":" + block.getPrestigeModifier()
                        + ":" + (block.getOccupiedBy() == null ? "-" : block.getOccupiedBy().getNickname()))
                .toList();
    }

    private static List<String> actualStartingBlockRows(SavedMatchAggregate aggregate) {
        return aggregate.startingBlocks().stream()
                .sorted(Comparator.comparingInt(SavedMatchStartingBlockRecord::blockPosition))
                .map(record -> record.blockPosition()
                        + ":" + record.foodModifier()
                        + ":" + record.prestigeModifier()
                        + ":" + (record.occupiedByNickname() == null ? "-" : record.occupiedByNickname()))
                .toList();
    }

    private static List<String> expectedBoardCardRows(Game game) {
        List<String> rows = new ArrayList<>();
        appendBoardRows(rows, SavedMatchBoardCardZone.UPPER_TRIBE, extractNonBuildingIds(game.getBoard().getUpperRowCards()));
        appendBoardRows(rows, SavedMatchBoardCardZone.UPPER_BUILDING, extractBuildingIds(game.getBoard().getUpperRowCards()));
        appendBoardRows(rows, SavedMatchBoardCardZone.LOWER_TRIBE, extractNonBuildingIds(game.getBoard().getLowerRowCards()));
        appendBoardRows(rows, SavedMatchBoardCardZone.LOWER_BUILDING, extractBuildingIds(game.getBoard().getLowerRowCards()));
        appendBoardRows(rows, SavedMatchBoardCardZone.TRIBE_DECK, game.getBoard().getRemainingTribeDeckCards().stream().map(Card::getId).toList());
        appendBoardRows(rows, SavedMatchBoardCardZone.BUILDING_DECK, game.getBoard().getRemainingBuildingDeckCards().stream().map(Card::getId).toList());
        return rows;
    }

    private static List<String> actualBoardCardRows(SavedMatchAggregate aggregate) {
        return aggregate.boardCards().stream()
                .sorted(Comparator.comparing(SavedMatchBoardCardRecord::cardZone)
                        .thenComparingInt(SavedMatchBoardCardRecord::positionIndex))
                .map(record -> record.cardZone() + ":" + record.positionIndex() + ":" + record.cardId())
                .toList();
    }

    private static List<String> expectedPlayerCardRows(Game game) {
        List<String> rows = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            int characterPosition = 0;
            for (var character : player.getTribe().getCharacters()) {
                rows.add(player.getNickname() + ":TRIBE_CHARACTER:" + characterPosition++ + ":" + character.getId());
            }

            int buildingPosition = 0;
            for (BuildingCard building : player.getTribe().getBuildings()) {
                rows.add(player.getNickname() + ":TRIBE_BUILDING:" + buildingPosition++ + ":" + building.getId());
            }
        }
        return rows;
    }

    private static List<String> actualPlayerCardRows(SavedMatchAggregate aggregate) {
        return aggregate.playerCards().stream()
                .sorted(Comparator.comparing(SavedMatchPlayerCardRecord::nickname)
                        .thenComparing(SavedMatchPlayerCardRecord::cardZone)
                        .thenComparingInt(SavedMatchPlayerCardRecord::positionIndex))
                .map(record -> record.nickname()
                        + ":" + record.cardZone()
                        + ":" + record.positionIndex()
                        + ":" + record.cardId())
                .toList();
    }

    private static List<String> expectedPlayerRuntimeRows(Game game) {
        return game.getPlayers().stream()
                .flatMap(player -> player.getTribe().getBuildings().stream()
                        .flatMap(building -> building.exportRuntimeState().entrySet().stream()
                                .map(entry -> player.getNickname()
                                        + ":" + building.getId()
                                        + ":" + entry.getKey()
                                        + ":" + entry.getValue())))
                .sorted()
                .toList();
    }

    private static List<String> actualPlayerRuntimeRows(SavedMatchAggregate aggregate) {
        return aggregate.playerCardRuntimeStates().stream()
                .sorted(Comparator.comparing(SavedMatchPlayerCardRuntimeStateRecord::nickname)
                        .thenComparingInt(SavedMatchPlayerCardRuntimeStateRecord::cardId)
                        .thenComparing(SavedMatchPlayerCardRuntimeStateRecord::stateKey))
                .map(record -> record.nickname()
                        + ":" + record.cardId()
                        + ":" + record.stateKey()
                        + ":" + record.stateIntValue())
                .toList();
    }

    private static List<Integer> extractNonBuildingIds(List<Card> cards) {
        return cards.stream()
                .filter(card -> !(card instanceof BuildingCard))
                .map(Card::getId)
                .toList();
    }

    private static List<Integer> extractBuildingIds(List<Card> cards) {
        return cards.stream()
                .filter(BuildingCard.class::isInstance)
                .map(Card::getId)
                .toList();
    }

    private static void appendBoardRows(List<String> rows, SavedMatchBoardCardZone zone, List<Integer> cardIds) {
        for (int position = 0; position < cardIds.size(); position++) {
            rows.add(zone + ":" + position + ":" + cardIds.get(position));
        }
    }

    private static Card createCard(int id) {
        return ParserJson.getDeckTemplate().createCardById(id);
    }

    private static TribeCard createTribe(int id) {
        return (TribeCard) createCard(id);
    }

    private static CharacterCard createCharacter(int id) {
        return (CharacterCard) createCard(id);
    }

    private static BuildingCard createBuilding(int id) {
        return (BuildingCard) createCard(id);
    }

    private static List<Integer> pickCharacterIds(int playerCount, Set<Integer> excludedIds, int count) {
        return ParserJson.parseTribeDeck(playerCount).getCardsInOrder().stream()
                .filter(CharacterCard.class::isInstance)
                .map(Card::getId)
                .filter(id -> !excludedIds.contains(id))
                .limit(count)
                .toList();
    }

    private static List<Integer> pickTribeIds(int playerCount, Set<Integer> excludedIds, int count) {
        return ParserJson.parseTribeDeck(playerCount).getCardsInOrder().stream()
                .map(Card::getId)
                .filter(id -> !excludedIds.contains(id))
                .limit(count)
                .toList();
    }

    private static List<Integer> pickBuildingIds(Set<Integer> excludedIds, int count) {
        return ParserJson.parseBuildingDeck().getCardsInOrder().stream()
                .map(Card::getId)
                .filter(id -> !excludedIds.contains(id))
                .limit(count)
                .toList();
    }
}
