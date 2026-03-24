package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.controller.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Reusable deep assertions for comparing live and restored match state.
 */
public final class GameStateAssertions {
    private GameStateAssertions() {
    }

    /**
     * Asserts that two games expose the same observable runtime state.
     *
     * @param expected expected game state
     * @param actual restored or compared game state
     */
    public static void assertEquivalent(Game expected, Game actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCurrentEra(), actual.getCurrentEra());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getTurnOrderTrack().getRoundNumber(), actual.getTurnOrderTrack().getRoundNumber());
        assertEquals(expected.getTurnOrderTrack().getCurrentRoundPhase(), actual.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(expected.getTurnOrderTrack().getCurrentPlayerIndex(), actual.getTurnOrderTrack().getCurrentPlayerIndex());
        assertEquals(expected.getTurnState().getPhase(), actual.getTurnState().getPhase());

        assertNicknameOrderEquals(expected.getPlayers(), actual.getPlayers());
        assertPlayerResourcesEqual(expected.getPlayers(), actual.getPlayers());
        assertNicknameOrderEquals(expected.getTurnOrderTrack().getTurnOrder(), actual.getTurnOrderTrack().getTurnOrder());
        assertChoiceStateEquals(expected, actual);
        assertCardOrderEquals(expected.getBoard().getUpperRowCards(), actual.getBoard().getUpperRowCards());
        assertCardOrderEquals(expected.getBoard().getLowerRowCards(), actual.getBoard().getLowerRowCards());
        assertCardOrderEquals(expected.getBoard().getRemainingTribeDeckCards(), actual.getBoard().getRemainingTribeDeckCards());
        assertCardOrderEquals(expected.getBoard().getRemainingBuildingDeckCards(), actual.getBoard().getRemainingBuildingDeckCards());
        assertEquals(occupiedOfferCodes(expected), occupiedOfferCodes(actual));
        assertEquals(startingBlocksState(expected), startingBlocksState(actual));
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

    private static List<String> occupiedOfferCodes(Game game) {
        return game.getBoard().getOfferSpaces().stream()
                .filter(space -> space.getOccupiedBy() != null)
                .sorted(Comparator.comparingInt(OfferSpace::getPositionIndex))
                .map(space -> space.getCode() + ":" + space.getOccupiedBy().getNickname())
                .toList();
    }

    private static List<String> startingBlocksState(Game game) {
        return game.getBoard().getStartingBlocks().stream()
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
