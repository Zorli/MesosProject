package it.polimi.ingsoft.mesos.server.controller.Factory;

import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;

/**
 * Default singleton implementation of {@link GameFactory}.
 *
 * <p>The factory reuses the immutable {@link DeckTemplate} parsed at startup and creates a fully
 * initialized {@link Game} with fresh decks, a new board, and turn-tracking components for each
 * request. The game ID is always supplied by the caller (lobby ID) — the factory never generates IDs.
 */
public class DefaultGameFactory implements GameFactory {
    private static final DefaultGameFactory instance = new DefaultGameFactory();
    private static final DeckTemplate deckTemplate = ParserJson.getDeckTemplate();

    private DefaultGameFactory() {
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
     * Builds a complete game model using an externally provided identifier.
     *
     * @param gameId identifier to assign to the game
     * @param players players that will participate in the match
     * @return a fully initialized game ready to be orchestrated
     */
    @Override
    public Game createGame(int gameId, List<Player> players) {
        TribeDeck tribeDeck = deckTemplate.createTribeDeck(players.size());
        BuildingDeck buildingDeck = deckTemplate.createBuildingDeck();
        Board board = new Board(tribeDeck, buildingDeck, players);
        PlayersTurnFSM playersTurnFSM = new PlayersTurnFSM();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        return new Game(
                gameId,
                players,
                playersTurnFSM,
                turnOrderTrack,
                board
        );
    }
}
