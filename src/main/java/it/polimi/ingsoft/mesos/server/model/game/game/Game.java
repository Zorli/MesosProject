package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidConnectionState;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.*;

/**
 * Facade and aggregate root of one Mesos match.
 *
 * <p>This class is the main entry point of the server-side game model. The
 * controller and orchestrator call this class when a client asks to perform a
 * game action such as setup, totem placement, standard card drafting, or extra
 * card drafting.
 *
 * <p>The class owns the complete mutable state of the match:
 * <ul>
 *     <li>the players;</li>
 *     <li>the current era;</li>
 *     <li>the lifecycle status;</li>
 *     <li>the board;</li>
 *     <li>the current round flow;</li>
 *     <li>the standard card-draft state;</li>
 *     <li>the listeners that must receive payload updates.</li>
 * </ul>
 *
 * <p>The detailed game rules are intentionally delegated to small handlers.
 * {@code Game} coordinates those handlers, while each handler owns one specific
 * part of the rules. This keeps the aggregate readable: the public methods show
 * the high-level flow, and the detailed rule code stays in focused classes.
 *
 * <p>Payload notification is centralized through {@link GameActionExecutor}.
 * Public actions do not notify directly. They change the state; then the
 * executor sends exactly one payload if the state version changed.
 */
public class Game {
    private final int id;
    private final List<Player> players;
    private Era currentEra;


    private GameStatus status;
    private long stateVersion;
    private final Board board;
    private final RoundFlow roundFlow;
    private final PlayerDraftState playerDraftState;
    private final List<Listener> listeners;





    /**
     * Tracks which players are currently connected to the match.
     *
     * <p>The network layer detects connection changes, but the actual update is
     * still executed through {@link GameActionExecutor}. This keeps connection
     * changes ordered with normal game actions and guarantees that payload
     * notifications are produced by the same path.
     */
    private Map<Player,Boolean> connectedPlayers;


    private final GameActionExecutor actionExecutor;
    private final TotemPlacementHandler totemPlacementHandler;
    private final CardDraftHandler cardDraftHandler;
    private final PassiveRoundPhaseHandler passiveRoundPhaseHandler;


    /**
     * Creates a new match in setup status.
     *
     * <p>This constructor is used by the normal game factory when a lobby starts
     * a fresh match. The first round order is not initialized here because the
     * board setup happens in {@link #setup()}.
     *
     * @param id unique game identifier
     * @param players players participating in the match
     * @param board board prepared with fresh decks and the same players
     */
    public Game(int id, List<Player> players, Board board) {
        this.id = id;
        this.players = new ArrayList<>(Objects.requireNonNull(players, "Players cannot be null"));
        this.roundFlow = new RoundFlow();
        this.playerDraftState = new PlayerDraftState();
        this.board = Objects.requireNonNull(board, "Board cannot be null");
        this.currentEra = Era.first;
        this.status = GameStatus.SETUP;
        this.stateVersion = 0L;
        this.listeners = new ArrayList<>();



        this.actionExecutor = new GameActionExecutor(this);
        this.totemPlacementHandler = new TotemPlacementHandler(this, this.board, this.roundFlow, this.playerDraftState);
        this.cardDraftHandler = new CardDraftHandler(this, this.board, this.roundFlow, this.playerDraftState);
        this.passiveRoundPhaseHandler = new PassiveRoundPhaseHandler(this, this.board, this.roundFlow, this.playerDraftState);
        this.connectedPlayers = new HashMap<>();
        for (Player player : this.players) {
            this.connectedPlayers.put(player, true);
        }
    }

    /**
     * Marks a player as disconnected and lets the active phase react.
     *
     * <p>The method first updates {@link #connectedPlayers}. Then it delegates
     * to the handler that owns the current phase:
     * <ul>
     *     <li>during totem placement, the player can be skipped in the current
     *     totem order without moving their board position;</li>
     *     <li>during standard card drafting, the current disconnected player is
     *     returned to the starting tile and their remaining draft quota is
     *     cleared;</li>
     *     <li>during the extra draw, disconnecting the current extra-draw player
     *     simply skips that optional action.</li>
     * </ul>
     *
     * <p>After the phase-specific handling, passive phases may continue
     * automatically. For example, skipping the extra draw moves the game to
     * effect resolution.
     *
     * @param player player who disconnected
     */
    public void disconnectedPlayer(Player player) {
        this.actionExecutor.execute(() -> {
            this.connectedPlayers.put(player, false);
            //TODO: Ci sono alcuni while che presuppongono invariante di almeno un giocatore connesso,
            //Secondo me almeno un check e lanciare ccezzione senno scorriamo olktre i limiti della mappa
            //qui un bel check se la persona disconessa era l'ultima -> State = FINISHED
            if(!connectedPlayers.values().stream().anyMatch(Boolean::booleanValue)){
                //Non c'è più nessuno connesso posso interrompere la partita
                this.markStateChanged();
                this.finishGame();
                return;

            }
            if (this.roundFlow.getCurrentRoundPhase() == RoundPhase.PLACING_TOTEMS) {
                this.totemPlacementHandler.handlePlayerDisconnection(player);
            } else if (this.roundFlow.getCurrentRoundPhase() == RoundPhase.PLAYER_DRAWS) {
                this.cardDraftHandler.handlePlayerDisconnection(player);
            } else if (this.roundFlow.getCurrentRoundPhase() == RoundPhase.EXTRA_DRAWS) {
                this.cardDraftHandler.handleExtraDrawPlayerDisconnection(player);
            }

            this.passiveRoundPhaseHandler.advancePassivePhases();
            this.markStateChanged();
        });
    }


    /**
     * Marks a player as connected again.
     *
     * <p>No board position is changed here. Reconnection only changes the
     * connection map. Whether the player can still act depends on the current
     * phase order already stored in {@link RoundFlow}.
     *
     * @param player player who reconnected
     */
    public void reconnectedPlayer(Player player) {
        this.actionExecutor.execute(() -> {
            connectedPlayers.put(player, true);
            this.markStateChanged();
        });
    }




    /**
     * Returns the unique identifier of this match.
     *
     * @return game identifier
     */
    public int getId() {
        return this.id;
    }

    /**
     * Returns the current connection state of every player.
     *
     * <p>The map is used by round handlers when they need to build a phase order
     * or skip a disconnected current player.
     *
     * @return map from player to connection state
     */
    public Map<Player, Boolean> getConnectedPlayers() {
        return connectedPlayers;
    }


    /**
     * Returns the players that belong to this match.
     *
     * @return immutable list of players
     */
    public List<Player> getPlayers() {
        return this.players;
    }

    /**
     * Returns the era currently being played.
     *
     * @return current era
     */
    public Era getCurrentEra() {
        return this.currentEra;
    }

    /**
     * Returns the lifecycle status of the match.
     *
     * @return current game status
     */
    public GameStatus getStatus() {
        return this.status;
    }

    /**
     * Returns the current state version.
     *
     * <p>The version increases every time model state changes. The action
     * executor uses it to decide whether a payload must be sent.
     *
     * @return current state version
     */
    public long getStateVersion() {
        return this.stateVersion;
    }

    /**
     * Returns the board owned by this game.
     *
     * @return current board
     */
    public Board getBoard() {
        return this.board;
    }

    /**
     * Returns the object that tracks the current round phase and player order.
     *
     * @return current round flow
     */
    public RoundFlow getRoundFlow() {
        return this.roundFlow;
    }

    /**
     * Returns the object that tracks remaining standard card-draft quotas.
     *
     * @return current player draft state
     */
    public PlayerDraftState getPlayerDraftState() {
        return this.playerDraftState;
    }

    /**
     * Registers a listener that receives game payload updates.
     *
     * @param listener listener to register
     */
    public void addListener(Listener listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");

        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener listener to remove
     */
    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Sends an error payload to all listeners.
     *
     * <p>This method is used by {@link GameActionExecutor} when a public action
     * fails because the requested move is invalid.
     *
     * @param code error code, usually the exception simple class name
     * @param message human-readable error message
     */
    public void notifyError(String code, String message) {
        this.notifyListeners(GamePayloadMapper.toErrorPayload(code, message));
    }

    /**
     * Sends a phase error to one listener.
     *
     * <p>This method is kept for controller paths that need to reject a command
     * before executing a full game action.
     *
     * @param target listener that must receive the error
     */
    public void reportPhaseError(Listener target) {
        if (target != null) {
            target.notifyUpdate(GamePayloadMapper.toErrorPayload(
                    "GAME_ACTION_ERROR",
                    "Action not permitted in this phase."
            ));
        }
    }

    /**
     * Performs the initial setup of the match.
     *
     * <p>Setup builds the offer path, fills the initial visible card rows,
     * applies the initial food benefit, initializes the first round order, and
     * moves the game from {@link GameStatus#SETUP} to
     * {@link GameStatus#IN_PROGRESS}.
     */
    public void setup() {
        this.actionExecutor.execute(() -> {
            this.setupInternal();
        });
    }

    /**
     * Places the current player's totem on an offer space.
     *
     * @param player player placing the totem
     * @param tileCode selected offer-space code
     */
    public void moveTotem(Player player, String tileCode) {
        this.actionExecutor.execute(() -> {
            this.onlyOneConnected();
            this.totemPlacementHandler.moveTotem(player, tileCode);
            this.passiveRoundPhaseHandler.advancePassivePhases();
        });
    }

    /**
     * Makes the current player draw one standard card from the board.
     *
     * @param player player choosing the card
     * @param cardId selected card identifier
     */
    public void chooseCard(Player player, int cardId) {
        this.actionExecutor.execute(() -> {
            this.onlyOneConnected();
            this.cardDraftHandler.chooseCard(player, cardId);
            this.passiveRoundPhaseHandler.advancePassivePhases();
        });
    }

    /**
     * Makes the current player perform one end-of-round extra draw.
     *
     * @param player player choosing the extra card
     * @param cardId selected card identifier
     */
    public void extraDrawn(Player player, int cardId) {
        this.actionExecutor.execute(() -> {
            this.onlyOneConnected();
            this.cardDraftHandler.extraDrawn(player, cardId);
            this.passiveRoundPhaseHandler.advancePassivePhases();
        });
    }

    /**
     * Builds the current game payload.
     *
     * <p>The mapping details are kept in {@link GamePayloadMapper}; this method
     * exists so callers do not need to know which mapper class creates the DTO.
     *
     * @return payload representing the current game state
     */
    public Object buildPayload() {
        return GamePayloadMapper.from(this);
    }

    /**
     * Notifies all currently registered listeners.
     *
     * <p>The listener list is copied before iteration so a listener can safely
     * register or unregister another listener while notifications are being
     * delivered.
     *
     * @param payload payload to send
     */
    public void notifyListeners(Object payload) {
        List<Listener> currentListeners = new ArrayList<>(this.listeners);

        for (Listener listener : currentListeners) {
            listener.notifyUpdate(payload);
        }
    }

    /**
     * Marks that the model state changed.
     *
     * <p>Handlers call this method after applying a valid state transition. The
     * version is intentionally stored in {@code Game}, because notification is
     * based on the whole aggregate state, not on one handler.
     */
    void markStateChanged() {
        this.stateVersion = this.stateVersion + 1;
    }

    /**
     * Advances the current era to the next era.
     *
     * <p>The board decides when an era change is triggered during board reset.
     * The era itself belongs to {@code Game}, so the passive phase handler asks
     * this method to update it.
     */
    void advanceEra() {
        this.currentEra = this.currentEra.next();
    }

    /**
     * Marks the game as finished.
     *
     * <p>This method is called after final scoring has been applied.
     */
    void finishGame() {
        this.status = GameStatus.FINISHED;
    }

    public void closeGame() {
        this.actionExecutor.execute(() -> {
            this.finishGame();
            this.markStateChanged();
        });
    }

    /**
     * Performs the real setup work inside the action-executor wrapper.
     *
     * <p>The public {@link #setup()} method is responsible for using the common
     * action flow: catch invalid-state errors and notify listeners when the
     * model changes. This private method contains only the game setup rules.
     *
     * <p>Setup creates the offer path, fills the visible card rows, gives the
     * starting food bonus, initializes the first round order from the player
     * list, starts round one in {@link RoundPhase#PLACING_TOTEMS}, clears any
     * draft state, and finally changes the lifecycle status to
     * {@link GameStatus#IN_PROGRESS}.
     */
    private void setupInternal() {
        if (this.status != GameStatus.SETUP) {
            throw new InvalidGameStatusException("setup", this.status);
        }

        this.board.buildOfferPath();
        this.board.setupInitialRows();
        this.applyInitialBenefit();

        this.roundFlow.setCurrentPhaseOrder(this.players);
        this.roundFlow.advanceRoundNumber();
        this.roundFlow.setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);
        this.playerDraftState.resetForNewRound();

        this.status = GameStatus.IN_PROGRESS;
        this.markStateChanged();
    }

    /**
     * Gives the initial food bonus to every player.
     *
     * <p>The bonus depends only on the player's position in the starting player
     * list: first player gets 2 food, second and third get 3 food, every later
     * player gets 4 food.
     */
    private void applyInitialBenefit() {
        for (int playerIndex = 0; playerIndex < this.players.size(); playerIndex++) {
            Player player = this.players.get(playerIndex);
            int initialFood = this.calculateInitialFood(playerIndex);
            player.gainFood(initialFood);
        }
    }
    private void onlyOneConnected(){
        if(this.connectedPlayers.values().stream().filter(Boolean.TRUE::equals).count() == 1){
            throw  new InvalidConnectionState("only one connected");
        }
    }
    public Boolean getStartTimer(){
        return this.connectedPlayers.values().stream().filter(Boolean.TRUE::equals).count() == 1;
    }

    /**
     * Calculates the initial food bonus for one player index.
     *
     * @param playerIndex zero-based position in the starting player list
     * @return food amount assigned by setup rules
     */
    private int calculateInitialFood(int playerIndex) {
        if (playerIndex == 0) {
            return 2;
        }

        if (playerIndex == 1 || playerIndex == 2) {
            return 3;
        }

        return 4;
    }
    public void setStatus(GameStatus status) {
        this.status = status;
    }

}
