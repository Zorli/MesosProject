package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultGameFactory implements GameFactory {
    private AtomicInteger nextGameId = new AtomicInteger(0);
    private static final DefaultGameFactory instance = new DefaultGameFactory();
    private static final DeckTemplate deckTemplate = ParserJson.getDeckTemplate();

    private DefaultGameFactory() {
        this.nextGameId = new AtomicInteger(0);
    }

    public static DefaultGameFactory getInstance() {
        return instance;
    }

    @Override
    public Game createGame(ArrayList<Player> players) {
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
                board,
                tribeDeck,
                buildingDeck
        );
    }
}
