package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared builders and lookup helpers used by gameplay-oriented tests.
 */
public final class MesosTestFixtures {
    private static final String[] DEFAULT_NICKNAMES = {"Alice", "Bob", "Carol", "Dave", "Eve"};

    private MesosTestFixtures() {
    }

    /**
     * Creates players using the default test nicknames and totem order.
     *
     * @param playerCount number of players to build
     * @return mutable list of initialized test players
     */
    public static ArrayList<Player> createPlayers(int playerCount) {
        if (playerCount < 1 || playerCount > DEFAULT_NICKNAMES.length) {
            throw new IllegalArgumentException("Unsupported player count: " + playerCount);
        }

        String[] selectedNicknames = new String[playerCount];
        System.arraycopy(DEFAULT_NICKNAMES, 0, selectedNicknames, 0, playerCount);
        return createPlayers(selectedNicknames);
    }

    /**
     * Creates players for the provided nicknames preserving order and distinct totem colors.
     *
     * @param nicknames player nicknames in seating order
     * @return mutable list of initialized test players
     */
    public static ArrayList<Player> createPlayers(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] colors = TotemColor.values();

        for (int index = 0; index < nicknames.length; index++) {
            Player player = new Player(nicknames[index], 0, 0, colors[index], null);
            player.initializeGameAttributes();
            players.add(player);
        }

        return players;
    }

    /**
     * Returns the canonical placement sequence used by the existing test suite for each player count.
     *
     * @param playerCount number of players in the match
     * @return offer-space codes in legal placement order
     */
    public static List<String> placementSequenceFor(int playerCount) {
        return switch (playerCount) {
            case 2 -> List.of("B", "C");
            case 3 -> List.of("B", "C", "D");
            case 4 -> List.of("B", "C", "D", "E");
            case 5 -> List.of("B", "C", "D", "E", "F");
            default -> throw new IllegalArgumentException("Unsupported player count: " + playerCount);
        };
    }

    /**
     * Creates a deterministic factory for a two-player scripted gameplay scenario.
     *
     * <p>The deck order is chosen to exercise lobby start, legal/illegal draws, row reset,
     * lower-row event resolution, and save/restore continuation using only real catalog cards.
     *
     * @return scripted factory for the shared two-player gameplay scenario
     */
    public static ScriptedGameFactory createTwoPlayerGameplayFactory() {
        return new ScriptedGameFactory(
                List.of(
                        10, 14, 26,
                        85, 3, 86, 11, 15, 27,
                        12, 16, 19, 28, 13, 17
                ),
                List.of(97)
        );
    }

    /**
     * Creates a deterministic factory that preserves the full catalog order for the given player count.
     *
     * <p>The resulting matches do not rely on shuffling, which makes long gameplay flows and
     * save/restore lockstep assertions reproducible across test runs.
     *
     * @param playerCount number of players to support in the tribe deck
     * @return scripted factory using the entire production catalog in deterministic order
     */
    public static ScriptedGameFactory createFullCatalogFactory(int playerCount) {
        return new ScriptedGameFactory(
                ParserJson.parseTribeDeck(playerCount).getCardsInOrder().stream()
                        .map(Card::getId)
                        .toList(),
                ParserJson.parseBuildingDeck().getCardsInOrder().stream()
                        .map(Card::getId)
                        .toList()
        );
    }

    /**
     * Finds one visible card on the board by identifier.
     *
     * @param game game whose board must be scanned
     * @param cardId target card identifier
     * @return matching visible card
     */
    public static Card findVisibleBoardCard(Game game, int cardId) {
        return Stream.concat(
                        game.getBoard().getUpperRowCards().stream(),
                        game.getBoard().getLowerRowCards().stream()
                )
                .filter(card -> card.getId() == cardId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Card " + cardId + " is not visible on the board"));
    }

    /**
     * Finds one card in the upper row by identifier.
     *
     * @param game game whose upper row must be scanned
     * @param cardId target card identifier
     * @return matching upper-row card
     */
    public static Card findUpperRowCard(Game game, int cardId) {
        return game.getBoard().getUpperRowCards().stream()
                .filter(card -> card.getId() == cardId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Card " + cardId + " is not present in the upper row"));
    }

    /**
     * Finds one card in the lower row by identifier.
     *
     * @param game game whose lower row must be scanned
     * @param cardId target card identifier
     * @return matching lower-row card
     */
    public static Card findLowerRowCard(Game game, int cardId) {
        return game.getBoard().getLowerRowCards().stream()
                .filter(card -> card.getId() == cardId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Card " + cardId + " is not present in the lower row"));
    }
}
