package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate root representing the full mutable state of a single pre-match Lobby.
 *
 * <p>The Lobby tracks user enrollment, capacity checks, host assignment, and the lifecycle status that
 * gates when the host may start the match; the actual handoff to a game is performed by the controller.
 * It operates using an Observer Pattern (via {@link Listener} registration) to notify connected clients of
 * updates, similar to the pattern found in the main {@code Game} model.
 *
 * <p><strong>Domain Rules &amp; Transitions:</strong>
 * <ul>
 *   <li>The lobby capacity must be between {@link #MIN_PLAYERS} and {@link #MAX_PLAYERS}.</li>
 *   <li>The player who creates the lobby is designated as the initial host ({@link #hostNickname}).</li>
 *   <li>If the host leaves, host authority is automatically re-assigned to the next available player.</li>
 *   <li>If all players leave, the lobby status changes to {@link LobbyStatus#CLOSED}.</li>
 *   <li>The lobby status transitions to {@link LobbyStatus#READY} when it is full (i.e. number of players matches capacity).
 *       Only in {@link LobbyStatus#READY}  status can the match be started by the host.</li>
 * </ul>
 *
 * <p><strong>Error Handling Pattern:</strong>
 * State-modifying methods are wrapped by {@link #executeLobbyAction(Runnable, Listener)}, which runs the
 * domain logic and catches any {@code IllegalArgumentException}/{@code IllegalStateException} it throws.
 * On failure it emits an {@link ErrorPayload} — unicast to the offending client, or broadcast when no
 * specific target is given — and returns {@code false}. On success it only returns {@code true}: the
 * success notification (player joined, player left, match started) is sent by the controller, which
 * calls the {@code notify*} methods after the action returns.
 */
public class Lobby {
    /** The minimum number of players allowed in a match. */
    public static final int MIN_PLAYERS = 2;
    /** The maximum number of players allowed in a match. */
    public static final int MAX_PLAYERS = 5;

    /** Unique numeric identifier for the lobby. */
    private final int id;
    /** The maximum number of players chosen for this lobby. */
    private final int capacity;
    /** Current lobby players, keyed by nickname and ordered by join time. */
    private final Map<String, TotemColor> totemColorByNickname;
    /** Nickname of the host player who has privileges to start the game match. */
    private String hostNickname;
    /** Current status of the lobby lifecycle. */
    private LobbyStatus status;

    /** List of active listeners (virtual views) listening to state updates from this lobby. */
    private final transient List<Listener> listeners;
    /** Mapper used to transform lobby events into serialization-friendly payloads. */
    private final transient LobbyPayloadMapper payloadMapper;

    /**
     * Constructs a new Lobby with a specific ID, hosting player, capacity limit, and host totem color.
     *
     * <p>The creating player is immediately inserted as the first lobby member and the selected
     * totem color is reserved for that host. Because the lobby later builds game players from this
     * association, the host color is mandatory and must not be {@code null}.
     *
     * @param id the unique ID of the lobby
     * @param hostNickname the nickname of the host (first player to join)
     * @param capacity the maximum player capacity (must be between {@link #MIN_PLAYERS} and {@link #MAX_PLAYERS})
     * @param totemColor the totem color selected by the host while creating the lobby
     * @throws IllegalArgumentException if the capacity is out of bounds, the nickname is invalid,
     *                                  or the host totem color is missing
     */
    public Lobby(int id, String hostNickname, int capacity, TotemColor totemColor) {
        if (capacity < MIN_PLAYERS || capacity > MAX_PLAYERS) {
            throw new IllegalArgumentException("Capacity must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
        }

        this.id = id;
        this.capacity = capacity;
        this.totemColorByNickname = new LinkedHashMap<>();
        this.hostNickname = validateNickname(hostNickname);
        this.status = LobbyStatus.WAITING;
        this.validateTotemColorSelection(totemColor);
        this.totemColorByNickname.put(this.hostNickname, totemColor);
        this.listeners = new ArrayList<>();
        this.payloadMapper = new LobbyPayloadMapper();
    }

    // --- LISTENER MANAGEMENT (Observer Pattern) ---

    /**
     * Adds an active listener (virtual view connection) to be notified of updates.
     *
     * @param listener the listener interface to register
     * @throws NullPointerException if the listener is null
     */
    public void addListener(Listener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    /**
     * Removes a registered listener (virtual view connection) when they disconnect or leave.
     *
     * @param listener the listener interface to unregister
     */
    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    // --- PUBLIC METHODS (Wrappers invoked by Controllers) ---

    /**
     * Attempts to start the match from the lobby.
     *
     * @param nickname the nickname of the user requesting to start the match
     * @return {@code true} if the match started successfully, {@code false} otherwise
     */
    public boolean startMatch(String nickname) {
        // We pass null as the error unicast target to broadcast any start validation error
        return this.executeLobbyAction(() -> this.startMatchInternal(nickname), null);
    }

    /**
     * Attempts to add a new player to the lobby with the totem color selected before joining.
     *
     * <p>The color is validated together with the other lobby join rules. If the color is missing
     * or already assigned to another lobby member, the method sends an error payload to the requester
     * and returns {@code false}.
     *
     * @param nickname the nickname of the joining player
     * @param requesterView the client's listener interface to receive updates and error alerts
     * @param totemColor the totem color selected by the joining player
     * @return {@code true} if the player joined successfully, {@code false} otherwise
     */
    public boolean addPlayer(String nickname, Listener requesterView, TotemColor totemColor) {
        // We pass the requesterView as target to unicast any joining error directly to the offender
        return this.executeLobbyAction(() -> {
            this.addPlayerInternal(nickname, totemColor);
            this.addListener(requesterView);
        }, requesterView);
    }

    /**
     * Attempts to remove a player from the lobby.
     *
     * @param nickname the nickname of the leaving player
     * @param requesterView the client's listener interface (removed upon successful exit)
     * @return {@code true} if the player left successfully, {@code false} otherwise
     */
    public boolean removePlayer(String nickname, Listener requesterView) {
        // We pass null to broadcast lobby exit events/errors
        return this.executeLobbyAction(() -> {
            this.removePlayerInternal(nickname);
            this.removeListener(requesterView);
        }, null);
    }

    /**
     * Sends a phase error update to the target listener when they perform an out-of-phase action.
     *
     * @param target the target listener interface to receive the phase error notification
     */
    public void reportPhaseError(Listener target) {
        if (target != null) {
            target.notifyUpdate(new ErrorPayload("PHASE_ERROR", "Action not permitted in this phase."));
        }
    }

    /**
     * Broadcasts a notification to all listeners indicating a player has successfully joined.
     *
     * @param nickname the nickname of the joined player
     */
    public void notifyPlayerJoined(String nickname) {
        this.notifyListeners(this.payloadMapper.lobbyJoined(nickname, this));
    }

    /**
     * Broadcasts a notification indicating a player has left the lobby.
     *
     * @param nickname the nickname of the leaving player
     * @param leavingListener the connection of the player leaving (receives a direct unicast confirmation)
     */
    public void notifyPlayerLeft(String nickname, Listener leavingListener) {
        ServerPayload payload = this.payloadMapper.lobbyLeft(nickname, this, this.id);
        if (leavingListener != null) {
            leavingListener.notifyUpdate(this.payloadMapper.lobbyLeftConfirmation(nickname, this.id));
        }
        this.notifyListeners(payload);
    }

    /**
     * Unicasts a start match notification to the target listener.
     *
     * @param nickname the nickname of the host starting the match
     * @param target the listener to notify
     */
    public void notifyMatchStarted(String nickname, Listener target) {
        if (target != null) {
            target.notifyUpdate(this.payloadMapper.lobbyStarted(nickname, this));
        }
    }

    // --- PRIVATE METHODS (Domain Logic and Checks) ---

    /**
     * Domain logic to start the match. Validates host authorization, lobby capacity, and status.
     */
    private void startMatchInternal(String nickname) {
        if (!this.isHost(nickname)) {
            throw new IllegalStateException(nickname + " tried to start the match. Only the lobby host can start the match");
        }
        if (this.status != LobbyStatus.READY) {
            throw new IllegalStateException("Cannot start lobby with status: " + this.status);
        }
        this.status = LobbyStatus.STARTED;
    }

    /**
     * Domain logic to add a new player.
     *
     * <p>Validates nickname, lobby status, capacity, duplicate membership, and totem color
     * availability before mutating the player list and color association.
     */
    private void addPlayerInternal(String nickname, TotemColor totemColor) {
        String validatedNickname = validateNickname(nickname);
        if (this.status == LobbyStatus.CLOSED || this.status == LobbyStatus.STARTED) {
            throw new IllegalStateException("Cannot join lobby with status: " + this.status);
        }
        if (this.totemColorByNickname.containsKey(validatedNickname)) {
            throw new IllegalStateException("User already present in lobby: " + validatedNickname);
        }
        if (this.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }
        this.validateTotemColorSelection(totemColor);
        this.totemColorByNickname.put(validatedNickname, totemColor);
        if (this.canStart()) {
            this.status = LobbyStatus.READY;
        }
    }

    /**
     * Domain logic to remove a player. Re-assigns the host or transitions status if empty/incomplete.
     */
    private void removePlayerInternal(String nickname) {
        String validatedNickname = validateNickname(nickname);
        TotemColor removedColor = this.totemColorByNickname.remove(validatedNickname);
        if (removedColor == null) {
            throw new IllegalStateException("User not present in lobby: " + validatedNickname);
        }

        // Re-assign host role if the previous host left
        if (validatedNickname.equals(this.hostNickname)) {
            this.hostNickname = this.totemColorByNickname.isEmpty()
                    ? null
                    : this.totemColorByNickname.keySet().iterator().next();
        }

        if (this.isEmpty()) {
            this.status = LobbyStatus.CLOSED;
        } else if (!this.canStart() && this.status == LobbyStatus.READY) {
            this.status = LobbyStatus.WAITING;
        }
    }

    // --- ERROR-HANDLING WRAPPER ---

    /**
     * Runs a state-modifying domain action and handles only its error path.
     *
     * <p>Wraps the action in a try-catch that converts a thrown {@code IllegalArgumentException} or
     * {@code IllegalStateException} into a routed {@link ErrorPayload}. On success it does not notify
     * anything; the controller sends the success update via the {@code notify*} methods.
     *
     * @param action the domain logic callback to execute
     * @param errorUnicastTarget the specific listener to notify of errors (sends broadcast if null)
     * @return {@code true} if action completed without exceptions, {@code false} otherwise
     */
    private boolean executeLobbyAction(Runnable action, Listener errorUnicastTarget) {
        try {
            action.run();
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ErrorPayload errorPayload = new ErrorPayload(
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            if (errorUnicastTarget != null) {
                errorUnicastTarget.notifyUpdate(errorPayload);
            } else {
                this.notifyListeners(errorPayload);
            }
            return false;
        }
    }

    /**
     * Sends a serializable payload to all registered listeners.
     */
    private void notifyListeners(ServerPayload payload) {
        List<Listener> currentListeners = List.copyOf(this.listeners);
        for (Listener listener : currentListeners) {
            listener.notifyUpdate(payload);
        }
    }

    /**
     * Clears all registered listeners. Typically invoked during transition to the game match.
     */
    public void clearListeners() {
        this.listeners.clear();
    }

    // --- UTILITIES AND GETTERS ---

    /**
     * Validates that the nickname is not null or blank.
     */
    private static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("User nickname cannot be null or blank");
        }
        return nickname;
    }

    /**
     * Validates that a requested totem color is present and not already reserved.
     *
     * <p>The lobby stores one nickname-to-color association for each seated player.
     * When the requested color is already assigned, the exception message also
     * includes the colors that can still be selected by the client.
     *
     * @param totemColor the requested totem color
     * @throws IllegalArgumentException if the color is {@code null} or already assigned
     */
    private void validateTotemColorSelection(TotemColor totemColor) {
        if (totemColor == null) {
            throw new IllegalArgumentException("Totem color must be selected");
        }

        if (this.totemColorByNickname.containsValue(totemColor)) {
            List<TotemColor> availableTotemColors = new ArrayList<>();
            for (TotemColor color : TotemColor.values()) {
                if (!this.totemColorByNickname.containsValue(color)) {
                    availableTotemColors.add(color);
                }
            }

            throw new IllegalArgumentException(
                    "Totem color " + totemColor
                            + " is already taken. Available colors: "
                            + availableTotemColors
            );
        }
    }

    /** @return the unique numeric identifier of the lobby */
    public int getId() { return this.id; }

    /** @return the nickname of the current host player */
    public String getHostNickname() { return this.hostNickname; }

    /** @return a read-only snapshot list of current players in the lobby */
    public List<String> getPlayers() { return List.copyOf(this.totemColorByNickname.keySet()); }

    /**
     * Returns the lobby totem selections keyed by player nickname.
     *
     * <p>This is the lobby's single source of truth for player enrollment and
     * color selection. The map preserves lobby join order.
     *
     * @return map from player nickname to selected totem color
     */
    public Map<String, TotemColor> getPlayerTotemColors() {
        return Map.copyOf(this.totemColorByNickname);
    }

    /**
     * Returns the totem colors that are not currently selected by any lobby player.
     *
     * @return list of available totem colors
     */
    public List<TotemColor> getAvailableTotemColors() {
        List<TotemColor> availableTotemColors = new ArrayList<>();
        for (TotemColor color : TotemColor.values()) {
            if (!this.totemColorByNickname.containsValue(color)) {
                availableTotemColors.add(color);
            }
        }
        return List.copyOf(availableTotemColors);
    }

    /** @return the chosen capacity configuration for the lobby */
    public int getChosenCapacity() { return this.capacity; }

    /** @return the current status of the lobby */
    public LobbyStatus getStatus() { return this.status; }

    /** @return {@code true} if the player is currently in the lobby, {@code false} otherwise */
    public boolean hasPlayer(String nickname) { return this.totemColorByNickname.containsKey(validateNickname(nickname)); }
    /**
     * Checks if a player is permitted to join this lobby based on its status and limits.
     *
     * @param nickname the player nickname checking enrollment eligibility
     * @return {@code true} if player is eligible to join, {@code false} otherwise
     */
    public boolean canBeJoinedBy(String nickname) {
        String validatedNickname = validateNickname(nickname);
        return this.status != LobbyStatus.CLOSED
                && this.status != LobbyStatus.STARTED
                && !this.totemColorByNickname.containsKey(validatedNickname)
                && !this.isFull();
    }

    /** @return {@code true} if the nickname matches the host of the lobby */
    public boolean isHost(String nickname) { return this.hostNickname != null && this.hostNickname.equals(nickname); }

    /** @return {@code true} if player count has reached the capacity limit */
    public boolean canStart() { return this.totemColorByNickname.size() == this.capacity; }

    /** @return {@code true} if lobby is at capacity */
    public boolean isFull() { return this.totemColorByNickname.size() >= this.capacity; }

    /** @return {@code true} if lobby has no players remaining */
    public boolean isEmpty() { return this.totemColorByNickname.isEmpty(); }
}
