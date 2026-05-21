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
 * Domain aggregate of one Mesos match.
 *
 * <p>{@code Game} is the server-side model of a single match. It contains the
 * rules and the mutable domain state, but it does not decide which network
 * session is allowed to act. Session lookup, command ordering, and reconnection
 * routing are responsibilities of the game controller and of the server session
 * registry.
 *
 * <p>This class owns the complete match state:
 * <ul>
 *     <li>the players that belong to the match;</li>
 *     <li>the current era and lifecycle status;</li>
 *     <li>the board, offer path, card rows, and player positions;</li>
 *     <li>the current round phase and the phase player order;</li>
 *     <li>the standard draft state and remaining draw quotas;</li>
 *     <li>the connection state of each player inside this match;</li>
 *     <li>the listeners that receive game payload updates.</li>
 * </ul>
 *
 * <p>The public methods are intentionally high-level commands. They validate
 * the current state, delegate detailed rules to the proper handler, advance
 * passive phases, and mark the aggregate as changed. For example, totem
 * placement is delegated to {@link TotemPlacementHandler}, card drafting to
 * {@link CardDraftHandler}, and automatic phase transitions to
 * {@link PassiveRoundPhaseHandler}.
 *
 * <p>Connection resilience is modelled as part of the domain state through
 * {@link #connectedPlayers}. A disconnection marks the player as not connected
 * and lets the current phase skip or adapt the turn order. A reconnection only
 * marks the same player as connected again; it does not undo skipped actions and
 * does not move the player backwards in the round flow.
 *
 * <p>Payload notification is centralized through {@link GameActionExecutor}.
 * Public actions do not notify directly. They update the aggregate and the
 * executor sends one payload only when the state version changed.
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
     * Tracks the current connection state for every player in the match.
     *
     * <p>The network layer detects broken connections, but the model update is
     * still executed as a normal game action. This keeps disconnections ordered
     * with moves, draws, setup, and close commands, and makes notification use
     * the same {@link GameActionExecutor} path as every other state change.
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
     * Applies a real player disconnection to the domain model.
     *
     * <p>The controller calls this method when a queued transport disconnect is
     * accepted as a game event. From this point on the player is disconnected in
     * the match model too.
     *
     * <p>The method first marks the player as disconnected in
     * {@link #connectedPlayers}. If no player is connected anymore, the match is
     * finished because there is no remaining participant able to continue it.
     * During setup, the method only records the connection state: the initial
     * phase order will later be built from the players that are still connected.
     *
     * <p>During an active round, the current phase decides how to absorb the
     * disconnection:
     * <ul>
     *     <li>totem placement can skip the disconnected player in the placement
     *     order without changing the board position;</li>
     *     <li>standard card drafting can return the current disconnected player
     *     to the starting tile and clear their remaining draft quota;</li>
     *     <li>extra draw can skip the optional extra action.</li>
     * </ul>
     *
     * <p>After the phase-specific handling, passive phases are advanced so the
     * game does not remain blocked on a player that is no longer connected.
     *
     * @param player player who disconnected
     */
    public void disconnectedPlayer(Player player) {
        this.actionExecutor.execute(() -> {
            this.connectedPlayers.put(player, false);
            if(!connectedPlayers.values().stream().anyMatch(Boolean::booleanValue)){
                //Non c'è più nessuno connesso posso interrompere la partita
                this.markStateChanged();
                this.finishGame();
                return;

            }
            if (this.status == GameStatus.SETUP) {
                this.markStateChanged();
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
     * Applies a player reconnection to the domain model.
     *
     * <p>Reconnection restores only the connection flag of the existing player.
     * It does not create a new player, does not reset the board, and does not
     * rebuild the current phase order. If the player was skipped while offline,
     * that skipped action remains skipped. This keeps reconnection resilient
     * without changing already processed game rules.
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
     * applies the initial food benefit, initializes the first round order with
     * the players that are connected at setup time, and moves the game from
     * {@link GameStatus#SETUP} to {@link GameStatus#IN_PROGRESS}.
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

    /**
     * Closes the match from a server-side lifecycle decision.
     *
     * <p>This is used when the controller decides that the match must end even
     * without a normal final-scoring transition, for example after the last
     * connected player has stayed alone for the configured timeout.
     */
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
     * starting food bonus, initializes the first round order from the players
     * that are connected at that moment, starts round one in
     * {@link RoundPhase#PLACING_TOTEMS}, clears any draft state, and finally
     * changes the lifecycle status to {@link GameStatus#IN_PROGRESS}.
     */
    private void setupInternal() {
        if (this.status != GameStatus.SETUP) {
            throw new InvalidGameStatusException("setup", this.status);
        }

        this.board.buildOfferPath();
        this.board.setupInitialRows();
        this.applyInitialBenefit();

        this.roundFlow.setCurrentPhaseOrder(this.connectedPlayersInOrder());
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
    /**
     * Returns the starting order filtered to players that are currently connected.
     *
     * <p>This is used during setup so a player who disconnects before the match
     * leaves setup is not selected as the first active player.
     *
     * @return connected players in the original lobby order
     */
    private List<Player> connectedPlayersInOrder() {
        List<Player> connectedPlayers = new ArrayList<>();
        for (Player player : this.players) {
            if (Boolean.TRUE.equals(this.connectedPlayers.get(player))) {
                connectedPlayers.add(player);
            }
        }
        return connectedPlayers;
    }

    /**
     * Rejects normal player actions while only one player is connected.
     *
     * <p>With a single connected player, continuing normal actions would let one
     * participant advance the match while nobody else can react. The controller
     * starts a timeout in this state; if nobody reconnects before the timeout,
     * the match is closed.
     */
    private void onlyOneConnected(){
        if(this.connectedPlayers.values().stream().filter(Boolean.TRUE::equals).count() == 1){
            throw  new InvalidConnectionState("only one connected");
        }
    }
    /**
     * Tells the controller whether the last-player timeout must be started.
     *
     * @return {@code true} when exactly one player is connected
     */
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
    /**
     * Overrides the lifecycle status.
     *
     * <p>This setter is used by tests and controlled setup paths that need to put
     * the aggregate in a specific lifecycle state.
     *
     * @param status new lifecycle status
     */
    public void setStatus(GameStatus status) {
        this.status = status;
    }

}
