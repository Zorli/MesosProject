package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Test-only game factory that builds matches from a scripted card order.
 *
 * <p>The factory still uses real cards defined in {@code deckSetup.json} so save/restore tests keep
 * exercising the same production card catalog. Only the deck order becomes deterministic.
 */
public final class ScriptedGameFactory implements GameFactory {
    private final List<Integer> tribeDeckCardIds;
    private final List<Integer> buildingDeckCardIds;
    private final AtomicInteger nextGameId = new AtomicInteger();

    /**
     * Creates the scripted factory with the exact tribe/building card order to use in produced games.
     *
     * @param tribeDeckCardIds tribe-card identifiers in draw order
     * @param buildingDeckCardIds building-card identifiers in draw order
     */
    public ScriptedGameFactory(List<Integer> tribeDeckCardIds, List<Integer> buildingDeckCardIds) {
        this.tribeDeckCardIds = List.copyOf(Objects.requireNonNull(tribeDeckCardIds, "Tribe deck ids cannot be null"));
        this.buildingDeckCardIds = List.copyOf(Objects.requireNonNull(buildingDeckCardIds, "Building deck ids cannot be null"));
    }

    /**
     * Creates one game with deterministic decks and the usual domain collaborators.
     *
     * @param players players participating in the match
     * @return game backed by the scripted deck order
     */
    @Override
    public Game createGame(List<Player> players) {
        TribeDeck tribeDeck = TribeDeck.fromOrderedCards(this.instantiateCardsById(
                ParserJson.parseTribeDeck(5).getCardsInOrder(),
                this.tribeDeckCardIds
        ));
        BuildingDeck buildingDeck = BuildingDeck.fromOrderedCards(this.instantiateCardsById(
                ParserJson.parseBuildingDeck().getCardsInOrder(),
                this.buildingDeckCardIds
        ));

        Board board = new Board(tribeDeck, buildingDeck, players);
        return new Game(
                this.nextGameId.getAndIncrement(),
                players,
                new TurnState(),
                new TurnOrderTrack(),
                board
        );
    }

    private ArrayList<Card> instantiateCardsById(List<Card> availableCards, List<Integer> requestedIds) {
        Map<Integer, Card> cardsById = availableCards.stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

        ArrayList<Card> orderedCards = new ArrayList<>(requestedIds.size());
        for (Integer requestedId : requestedIds) {
            Card card = cardsById.get(requestedId);
            if (card == null) {
                throw new IllegalArgumentException("Unknown scripted card id: " + requestedId);
            }
            orderedCards.add(card);
        }

        return orderedCards;
    }
}
