package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Aggregate root that stores the full mutable state of one Mesos match.
 *
 * <p>The class owns the players, decks, board, current era, turn-state machine, and round-order
 * tracker. It exposes the high-level actions invoked by the orchestrator and applies the
 * corresponding state transitions to the match. Facade Design Pattern
 */
public class Game {
    private final int id;   //Immutable, provided by the game orchestrator
    private final List<Player> players;
    private Era currentEra; //Mutable
    private GameStatus status;  //Mutable
    private long stateVersion;
    private final Board board;

    

}