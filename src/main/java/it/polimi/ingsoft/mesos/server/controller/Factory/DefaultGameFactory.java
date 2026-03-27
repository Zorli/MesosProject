package it.polimi.ingsoft.mesos.server.controller.Factory;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default singleton implementation of {@link GameFactory}.
 *
 * <p>The factory reuses the immutable {@link DeckTemplate} parsed at startup and creates a fully
 * initialized {@link Game} with fresh decks, a new board, and turn-tracking components for each
 * request. An internal counter assigns a unique progressive identifier to every game instance.
 */
public class DefaultGameFactory implements GameFactory {
    private AtomicInteger nextGameId = new AtomicInteger(0);
    private static final DefaultGameFactory instance = new DefaultGameFactory();
    private static final DeckTemplate deckTemplate = ParserJson.getDeckTemplate();

    /**
     * Creates the singleton factory instance and resets the game id counter.
     */
    private DefaultGameFactory() {
        this.nextGameId = new AtomicInteger(0);
    }

    /**
     * Returns the shared factory used by the server to start new matches.
     *
     * @return the singleton game factory instance
     */
    public static DefaultGameFactory getInstance() {
        return instance;
    }

    /**
     * Builds a complete game model for the provided players.
     *
     * <p>The method creates building decks, prepares the board fed by
     * those decks, initializes the turn state and turn order tracker, and finally packages all of
     * them into a new {@link Game}.
     *
     * @param players players that will participate in the match
     * @return a fully initialized game ready to be orchestrated
     */
    @Override
    public Game createGame(List<Player> players) {
        TribeDeck tribeDeck = deckTemplate.createTribeDeck(players.size());
        BuildingDeck buildingDeck = deckTemplate.createBuildingDeck();
        Board board = new Board(tribeDeck, buildingDeck, players);
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        return new Game(
                nextGameId.getAndIncrement(),
                players,
                turnState,
                turnOrderTrack,
                board
        );
    }
}
