package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyPayloadMapper;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing the full mutable state of a single pre-match Lobby.
 *
 * <p>The Lobby manages user enrollment, capacity checks, host assignment, and transition to a live game match.
 * It operates using an Observer Pattern (via {@link Listener} registration) to notify connected clients of
 * updates, similar to the pattern found in the main {@code Game} model.
 *
 * <p><strong>Domain Rules & Transitions:</strong>
 * <ul>
 *   <li>The lobby capacity must be between {@link #MIN_PLAYERS} and {@link #MAX_PLAYERS}.</li>
 *   <li>The player who creates the lobby is designated as the initial host ({@link #hostNickname}).</li>
 *   <li>If the host leaves, host authority is automatically re-assigned to the next available player.</li>
 *   <li>If all players leave, the lobby status changes to {@link LobbyStatus#CLOSED}.</li>
 *   <li>The lobby status transitions to {@link LobbyStatus#READY} when it is full (i.e. number of players matches capacity).
 *       Only in {@link LobbyStatus#READY} or {@link LobbyStatus#RESUMABLE} status can the match be started by the host.</li>
 * </ul>
 *
 * <p><strong>Error Handling Pattern:</strong>
 * State-modifying methods are internally validated. Any illegal state or domain rule violation throws a runtime exception
 * which is caught by the central engine {@link #executeLobbyAction(Runnable, Listener)}. This wrapper notifies the client that
 * made the mistake via a unicast error payload or broadcasts it.
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
    /** Ordered list of nicknames representing current players in the lobby. */
    private final List<String> players;
    /** List of nicknames representing players allowed to rejoin a suspended (resumable) lobby. */
    private final List<String> resumableRoster;
    /** Nickname of the host player who has privileges to start the game match. */
    private String hostNickname;
    /** Current status of the lobby lifecycle. */
    private LobbyStatus status;

    /** List of active listeners (virtual views) listening to state updates from this lobby. */
    private final transient List<Listener> listeners;
    /** Mapper used to transform lobby events into serialization-friendly payloads. */
    private final transient LobbyPayloadMapper payloadMapper;

    /**
     * Constructs a new Lobby with a specific ID, hosting player, and capacity limit.
     *
     * @param id the unique ID of the lobby
     * @param hostNickname the nickname of the host (first player to join)
     * @param capacity the maximum player capacity (must be between {@link #MIN_PLAYERS} and {@link #MAX_PLAYERS})
     * @throws IllegalArgumentException if the capacity is out of bounds or nickname is invalid
     */
    public Lobby(int id, String hostNickname, int capacity) {
        if (capacity < MIN_PLAYERS || capacity > MAX_PLAYERS) {
            throw new IllegalArgumentException("Capacity must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
        }

        this.id = id;
        this.capacity = capacity;
        this.players = new ArrayList<>();
        this.resumableRoster = List.of();
        this.hostNickname = validateNickname(hostNickname);
        this.status = LobbyStatus.WAITING;
        this.players.add(this.hostNickname);

        this.listeners = new ArrayList<>();
        this.payloadMapper = new LobbyPayloadMapper();
    }

    // --- GESTIONE LISTENER (Observer Pattern) ---

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
     * Attempts to add a new player to the lobby.
     *
     * @param nickname the nickname of the joining player
     * @param requesterView the client's listener interface to receive updates and error alerts
     * @return {@code true} if the player joined successfully, {@code false} otherwise
     */
    public boolean addPlayer(String nickname, Listener requesterView) {
        // We pass the requesterView as target to unicast any joining error directly to the offender
        return this.executeLobbyAction(() -> {
            this.addPlayerInternal(nickname);
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
     * Attempts to rejoin a player to a resumable lobby.
     *
     * @param nickname the nickname of the rejoining player
     * @param requesterView the client's listener interface to register
     * @return {@code true} if the player rejoined successfully, {@code false} otherwise
     */
    public boolean rejoinResumablePlayer(String nickname, Listener requesterView) {
        return this.executeLobbyAction(() -> {
            this.rejoinResumablePlayerInternal(nickname);
            this.addListener(requesterView);
        }, requesterView);
    }

    /**
     * Sends a phase error update to the target listener when they perform an out-of-phase action.
     *
     * @param target the target listener interface to receive the phase error notification
     */
    public void reportPhaseError(Listener target) {
        if (target != null) {
            target.notifyUpdate(new ErrorPayload("PHASE_ERROR", "Action non permitted in this phase."));
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
        Object payload = this.payloadMapper.lobbyLeft(nickname, this, this.id);
        if (leavingListener != null) {
            leavingListener.notifyUpdate(payload);
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
            throw new IllegalStateException("Only the lobby host can start the match");
        }
        if (this.status != LobbyStatus.READY && this.status != LobbyStatus.RESUMABLE) {
            throw new IllegalStateException("Cannot start lobby with status: " + this.status);
        }
        if (!this.canStart()) {
            throw new IllegalStateException("Cannot start lobby that cannot start");
        }

        this.status = LobbyStatus.STARTED;
    }

    /**
     * Domain logic to add a new player. Validates capacity, duplicates, and status constraints.
     */
    private void addPlayerInternal(String nickname) {
        String validatedNickname = validateNickname(nickname);
        if (this.status == LobbyStatus.CLOSED || this.status == LobbyStatus.STARTED) {
            throw new IllegalStateException("Cannot join lobby with status: " + this.status);
        }
        if (this.players.contains(validatedNickname)) {
            throw new IllegalStateException("User already present in lobby: " + validatedNickname);
        }
        if (this.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }

        this.players.add(validatedNickname);
        if (this.canStart()) {
            this.status = LobbyStatus.READY;
        }
    }

    /**
     * Domain logic to rejoin a player to a resumable lobby.
     */
    private void rejoinResumablePlayerInternal(String nickname) {
        String validatedNickname = validateNickname(nickname);
        if (this.status != LobbyStatus.RESUMABLE) {
            throw new IllegalStateException("Cannot resume lobby with status: " + this.status);
        }
        if (!this.resumableRoster.contains(validatedNickname)) {
            throw new IllegalStateException("User not allowed to resume this lobby: " + validatedNickname);
        }
        if (this.players.contains(validatedNickname)) {
            throw new IllegalStateException("User already present in lobby: " + validatedNickname);
        }
        this.players.add(validatedNickname);
    }

    /**
     * Domain logic to remove a player. Re-assigns the host or transitions status if empty/incomplete.
     */
    private void removePlayerInternal(String nickname) {
        String validatedNickname = validateNickname(nickname);
        boolean removed = this.players.remove(validatedNickname);
        if (!removed) {
            throw new IllegalStateException("User not present in lobby: " + validatedNickname);
        }

        // Re-assign host role if the previous host left
        if (validatedNickname.equals(this.hostNickname)) {
            this.hostNickname = this.players.isEmpty() ? null : this.players.get(0);
        }

        // Update status based on remaining players
        if (this.isEmpty()) {
            this.status = LobbyStatus.CLOSED;
        } else if (!this.canStart() && this.status == LobbyStatus.READY) {
            this.status = LobbyStatus.WAITING;
        }
    }

    // --- CENTRAL EXECUTION ENGINE ---

    /**
     * Standard runner for domain model modifications. Wraps validation actions in try-catch blocks
     * to safely construct and route {@link ErrorPayload}s.
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
    private void notifyListeners(Object payload) {
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

    /** @return the unique numeric identifier of the lobby */
    public int getId() { return this.id; }

    /** @return the nickname of the current host player */
    public String getHostNickname() { return this.hostNickname; }

    /** @return a read-only snapshot list of current players in the lobby */
    public List<String> getPlayers() { return List.copyOf(this.players); }

    /** @return the chosen capacity configuration for the lobby */
    public int getChosenCapacity() { return this.capacity; }

    /** @return the current status of the lobby */
    public LobbyStatus getStatus() { return this.status; }

    /** @return {@code true} if the player is currently in the lobby, {@code false} otherwise */
    public boolean hasPlayer(String nickname) { return this.players.contains(validateNickname(nickname)); }

    /** @return {@code true} if the player is expected to reconnect to this suspended lobby */
    public boolean isExpectedResumePlayer(String nickname) { return this.resumableRoster.contains(validateNickname(nickname)); }

    /**
     * Checks if a player is permitted to join this lobby based on its status and limits.
     *
     * @param nickname the player nickname checking enrollment eligibility
     * @return {@code true} if player is eligible to join, {@code false} otherwise
     */
    public boolean canBeJoinedBy(String nickname) {
        String validatedNickname = validateNickname(nickname);
        if (this.status == LobbyStatus.RESUMABLE) {
            return this.resumableRoster.contains(validatedNickname) && !this.players.contains(validatedNickname);
        }
        return this.status != LobbyStatus.CLOSED
                && this.status != LobbyStatus.STARTED
                && !this.players.contains(validatedNickname)
                && !this.isFull();
    }

    /** @return {@code true} if the nickname matches the host of the lobby */
    public boolean isHost(String nickname) { return this.hostNickname != null && this.hostNickname.equals(nickname); }

    /** @return {@code true} if player count has reached the capacity limit */
    public boolean canStart() { return this.players.size() == this.capacity; }

    /** @return {@code true} if lobby is at capacity */
    public boolean isFull() { return this.players.size() >= this.capacity; }

    /** @return {@code true} if lobby has no players remaining */
    public boolean isEmpty() { return this.players.isEmpty(); }
}
