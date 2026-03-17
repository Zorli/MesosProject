package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresSavedMatchServiceIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void saveAndRestorePreservesInitialBoardAndDeckOrder() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_restore_setup", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        PostgresSavedMatchService service = new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );

        Game game = createGame();
        game.setup();

        service.saveMatch(game, "Alice", SavedMatchCheckpointType.GAME_INITIALIZED);

        Game restoredGame = service.restoreGame(String.valueOf(game.getId()));

        assertEquals(game.getId(), restoredGame.getId());
        assertEquals(game.getCurrentEra(), restoredGame.getCurrentEra());
        assertEquals(game.getStatus(), restoredGame.getStatus());
        assertEquals(game.getTurnOrderTrack().getRoundNumber(), restoredGame.getTurnOrderTrack().getRoundNumber());
        assertEquals(game.getTurnOrderTrack().getCurrentRoundPhase(), restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(game.getTurnOrderTrack().getCurrentPlayerIndex(), restoredGame.getTurnOrderTrack().getCurrentPlayerIndex());
        assertEquals(game.getTurnState().getPhase(), restoredGame.getTurnState().getPhase());
        assertNicknameOrderEquals(game.getTurnOrderTrack().getTurnOrder(), restoredGame.getTurnOrderTrack().getTurnOrder());
        assertCardOrderEquals(game.getBoard().getUpperRowCards(), restoredGame.getBoard().getUpperRowCards());
        assertCardOrderEquals(game.getBoard().getLowerRowCards(), restoredGame.getBoard().getLowerRowCards());
        assertCardOrderEquals(game.getBoard().getRemainingTribeDeckCards(), restoredGame.getBoard().getRemainingTribeDeckCards());
        assertCardOrderEquals(game.getBoard().getRemainingBuildingDeckCards(), restoredGame.getBoard().getRemainingBuildingDeckCards());
    }

    @Test
    void saveAndRestorePreservesPartialTurnStateAndOfferOccupancy() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_restore_turn", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        PostgresSavedMatchService service = new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );

        Game game = createGame();
        game.setup();
        Player firstPlayer = game.getTurnOrderTrack().getCurrentPlayer();
        game.moveTotem(firstPlayer, "B");

        service.saveMatch(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);

        Game restoredGame = service.restoreGame(String.valueOf(game.getId()));

        assertEquals(game.getTurnOrderTrack().getCurrentPlayerIndex(), restoredGame.getTurnOrderTrack().getCurrentPlayerIndex());
        assertEquals(game.getTurnState().getRemainingLowerChoices(firstPlayer), restoredGame.getTurnState().getRemainingLowerChoices(restoredGame.getPlayers().getFirst()));
        assertEquals(
                occupiedOfferCodes(game.getBoard().getAvailableOfferSpaces()),
                occupiedOfferCodes(restoredGame.getBoard().getAvailableOfferSpaces())
        );
        assertCardOrderEquals(game.getBoard().getRemainingTribeDeckCards(), restoredGame.getBoard().getRemainingTribeDeckCards());
    }

    @Test
    void markPlayerOnlineRejectsSecondDifferentSessionToken() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_presence", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        PostgresSavedMatchService service = new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );

        Game game = createGame();
        game.setup();
        service.saveMatch(game, "Alice", SavedMatchCheckpointType.GAME_INITIALIZED);

        service.markPlayerOnline(String.valueOf(game.getId()), "Alice", "session-1");

        assertThrows(
                SavedMatchPlayerConnectionException.class,
                () -> service.markPlayerOnline(String.valueOf(game.getId()), "Alice", "session-2")
        );
    }

    @Test
    void restoreRoundTripPreservesStateAndAllowsContinuationForTwoToFivePlayers() {
        for (int playerCount = 2; playerCount <= 5; playerCount++) {
            ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_roundtrip_" + playerCount, true);
            PostgresDatabaseBootstrap.getInstance().ensureReady(
                    runtimeConfig,
                    List.of(PostgresSavedMatchSchemaModule.getInstance())
            );

            PostgresSavedMatchService service = new PostgresSavedMatchService(
                    runtimeConfig.toPostgresConnectionConfig(),
                    runtimeConfig.maxBusyRetries()
            );

            Game originalGame = createGame(playerCount);
            originalGame.setup();

            List<String> placementSequence = placementSequenceFor(playerCount);
            int restorePoint = Math.max(1, playerCount / 2);

            playPlacements(originalGame, placementSequence, restorePoint);
            service.saveMatch(originalGame, originalGame.getPlayers().getFirst().getNickname(), SavedMatchCheckpointType.TURN_STARTED);

            Game restoredGame = service.restoreGame(String.valueOf(originalGame.getId()));
            assertGameStatesEqual(originalGame, restoredGame);

            for (int index = restorePoint; index < placementSequence.size(); index++) {
                String tile = placementSequence.get(index);
                originalGame.moveTotem(originalGame.getTurnOrderTrack().getCurrentPlayer(), tile);
                restoredGame.moveTotem(restoredGame.getTurnOrderTrack().getCurrentPlayer(), tile);
                assertGameStatesEqual(originalGame, restoredGame);
            }

            assertEquals(RoundPhase.PLAYER_DRAWS, originalGame.getTurnOrderTrack().getCurrentRoundPhase());
            assertGameStatesEqual(originalGame, restoredGame);
        }
    }

    @Test
    void restoreRoundTripPreservesMutableBuildingRuntimeState() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_building_state", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        PostgresSavedMatchService service = new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );

        Game game = createGame();
        game.setup();

        SetForFood setForFood = (SetForFood) ParserJson.parseBuildingDeck().getCardsInOrder().stream()
                .filter(SetForFood.class::isInstance)
                .findFirst()
                .orElseThrow();
        setForFood.restoreRuntimeState(Map.of(
                "HUNTER", 1,
                "BUILDER", 2,
                "GATHERER", 0,
                "ARTIST", 1,
                "INVENTOR", 0,
                "SHAMAN", 3
        ));

        PairOfInventor pairOfInventor = (PairOfInventor) ParserJson.parseBuildingDeck().getCardsInOrder().stream()
                .filter(PairOfInventor.class::isInstance)
                .findFirst()
                .orElseThrow();
        pairOfInventor.restoreRuntimeState(Map.of(
                "BOW", 0,
                "FIRE", 1,
                "WHEEL", 0,
                "KNIFE", 1,
                "RAFT", 0,
                "BASKET", 1,
                "SPEAR", 0,
                "TENT", 1,
                "NECKLACE", 0,
                "PAINTING", 1
        ));

        game.getPlayers().getFirst().getTribe().addBuilding(setForFood);
        game.getPlayers().getFirst().getTribe().addBuilding(pairOfInventor);

        service.saveMatch(game, "Alice", SavedMatchCheckpointType.TURN_STARTED);

        Game restoredGame = service.restoreGame(String.valueOf(game.getId()));
        assertGameStatesEqual(game, restoredGame);
    }

    @Test
    void multipleConcurrentSavedMatchesRemainIsolatedInsideTheSameDatabase() {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig("saved_matches_isolation", true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        PostgresSavedMatchService service = new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );

        Game twoPlayerGame = createGame(2);
        twoPlayerGame.setup();
        playPlacements(twoPlayerGame, placementSequenceFor(2), 1);
        service.saveMatch(twoPlayerGame, "Alice", SavedMatchCheckpointType.TURN_STARTED);

        Game fourPlayerGame = createGame(4);
        fourPlayerGame.setup();
        playPlacements(fourPlayerGame, placementSequenceFor(4), 3);
        service.saveMatch(fourPlayerGame, "Alice", SavedMatchCheckpointType.TURN_STARTED);

        Game restoredTwoPlayerGame = service.restoreGame(String.valueOf(twoPlayerGame.getId()));
        Game restoredFourPlayerGame = service.restoreGame(String.valueOf(fourPlayerGame.getId()));

        assertGameStatesEqual(twoPlayerGame, restoredTwoPlayerGame);
        assertGameStatesEqual(fourPlayerGame, restoredFourPlayerGame);
        assertEquals(2, restoredTwoPlayerGame.getPlayers().size());
        assertEquals(4, restoredFourPlayerGame.getPlayers().size());
    }

    private static Game createGame() {
        return createGame(2);
    }

    private static Game createGame(int playerCount) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        String[] nicknames = new String[]{"Alice", "Bob", "Carol", "Dave", "Eve"};
        for (int index = 0; index < playerCount; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], null));
        }
        return DefaultGameFactory.getInstance().createGame(players);
    }

    private static void playPlacements(Game game, List<String> placementSequence, int movesToPlay) {
        for (int index = 0; index < movesToPlay; index++) {
            game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), placementSequence.get(index));
        }
    }

    private static List<String> placementSequenceFor(int playerCount) {
        return switch (playerCount) {
            case 2 -> List.of("B", "C");
            case 3 -> List.of("B", "C", "D");
            case 4 -> List.of("B", "C", "D", "E");
            case 5 -> List.of("B", "C", "D", "E", "F");
            default -> throw new IllegalArgumentException("Unsupported player count: " + playerCount);
        };
    }

    private static void assertGameStatesEqual(Game expected, Game actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCurrentEra(), actual.getCurrentEra());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getTurnOrderTrack().getRoundNumber(), actual.getTurnOrderTrack().getRoundNumber());
        assertEquals(expected.getTurnOrderTrack().getCurrentRoundPhase(), actual.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(expected.getTurnOrderTrack().getCurrentPlayerIndex(), actual.getTurnOrderTrack().getCurrentPlayerIndex());
        assertEquals(expected.getTurnState().getPhase(), actual.getTurnState().getPhase());
        assertEquals(
                expected.getTurnState().getActivePlayer() == null ? null : expected.getTurnState().getActivePlayer().getNickname(),
                actual.getTurnState().getActivePlayer() == null ? null : actual.getTurnState().getActivePlayer().getNickname()
        );
        assertNicknameOrderEquals(expected.getPlayers(), actual.getPlayers());
        assertPlayerResourcesEqual(expected.getPlayers(), actual.getPlayers());
        assertNicknameOrderEquals(expected.getTurnOrderTrack().getTurnOrder(), actual.getTurnOrderTrack().getTurnOrder());
        assertChoiceStateEquals(expected, actual);
        assertCardOrderEquals(expected.getBoard().getUpperRowCards(), actual.getBoard().getUpperRowCards());
        assertCardOrderEquals(expected.getBoard().getLowerRowCards(), actual.getBoard().getLowerRowCards());
        assertCardOrderEquals(expected.getBoard().getRemainingTribeDeckCards(), actual.getBoard().getRemainingTribeDeckCards());
        assertCardOrderEquals(expected.getBoard().getRemainingBuildingDeckCards(), actual.getBoard().getRemainingBuildingDeckCards());
        assertEquals(occupiedOfferCodes(expected.getBoard().getAvailableOfferSpaces()), occupiedOfferCodes(actual.getBoard().getAvailableOfferSpaces()));
        assertEquals(startingBlocksState(expected.getBoard().getStartingBlocks()), startingBlocksState(actual.getBoard().getStartingBlocks()));
        assertEquals(playerCardState(expected.getPlayers()), playerCardState(actual.getPlayers()));
        assertEquals(playerCardRuntimeState(expected.getPlayers()), playerCardRuntimeState(actual.getPlayers()));
    }

    private static void assertCardOrderEquals(List<Card> expected, List<Card> actual) {
        assertIterableEquals(cardIds(expected), cardIds(actual));
    }

    private static List<Integer> cardIds(List<Card> cards) {
        return cards.stream()
                .map(Card::getId)
                .toList();
    }

    private static void assertNicknameOrderEquals(List<Player> expected, List<Player> actual) {
        assertIterableEquals(
                expected.stream().map(Player::getNickname).toList(),
                actual.stream().map(Player::getNickname).toList()
        );
    }

    private static void assertPlayerResourcesEqual(List<Player> expected, List<Player> actual) {
        for (int index = 0; index < expected.size(); index++) {
            Player expectedPlayer = expected.get(index);
            Player actualPlayer = actual.get(index);
            assertEquals(expectedPlayer.getFood(), actualPlayer.getFood());
            assertEquals(expectedPlayer.getPrestigePoints(), actualPlayer.getPrestigePoints());
            assertEquals(expectedPlayer.getTotem(), actualPlayer.getTotem());
        }
    }

    private static void assertChoiceStateEquals(Game expected, Game actual) {
        for (int index = 0; index < expected.getPlayers().size(); index++) {
            Player expectedPlayer = expected.getPlayers().get(index);
            Player actualPlayer = actual.getPlayers().get(index);
            assertEquals(
                    expected.getTurnState().getRemainingUpperChoices(expectedPlayer),
                    actual.getTurnState().getRemainingUpperChoices(actualPlayer)
            );
            assertEquals(
                    expected.getTurnState().getRemainingLowerChoices(expectedPlayer),
                    actual.getTurnState().getRemainingLowerChoices(actualPlayer)
            );
        }
    }

    private static List<String> occupiedOfferCodes(List<OfferSpace> offerSpaces) {
        return offerSpaces.stream()
                .filter(space -> space.getOccupiedBy() != null)
                .sorted(Comparator.comparingInt(OfferSpace::getPositionIndex))
                .map(space -> space.getCode() + ":" + space.getOccupiedBy().getNickname())
                .toList();
    }

    private static List<String> startingBlocksState(List<StartingBlock> startingBlocks) {
        return startingBlocks.stream()
                .sorted(Comparator.comparingInt(StartingBlock::getPosition))
                .map(block -> block.getPosition() + ":" + (block.getOccupiedBy() == null ? "-" : block.getOccupiedBy().getNickname()))
                .toList();
    }

    private static List<String> playerCardState(List<Player> players) {
        return players.stream()
                .flatMap(player -> java.util.stream.Stream.concat(
                        player.getTribe().getCharacters().stream()
                                .map(card -> player.getNickname() + ":CHAR:" + card.getId()),
                        player.getTribe().getBuildings().stream()
                                .map(card -> player.getNickname() + ":BUILD:" + card.getId())
                ))
                .toList();
    }

    private static List<String> playerCardRuntimeState(List<Player> players) {
        return players.stream()
                .flatMap(player -> player.getTribe().getBuildings().stream()
                        .flatMap(buildingCard -> runtimeStateRows(player.getNickname(), buildingCard).stream()))
                .sorted()
                .toList();
    }

    private static List<String> runtimeStateRows(String nickname, BuildingCard buildingCard) {
        return buildingCard.exportRuntimeState().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> nickname + ":" + buildingCard.getId() + ":" + entry.getKey() + "=" + entry.getValue())
                .toList();
    }
}
