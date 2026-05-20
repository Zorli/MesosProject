package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root that stores the full mutable state of one pre-match Lobby.
 * Funziona con lo stesso pattern architetturale della classe Game.
 */
public class Lobby {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 5;

    private final int id;
    private final int capacity;
    private final List<String> players;
    private final List<String> resumableRoster;
    private String hostNickname;
    private LobbyStatus status;

    private final transient List<Listener> listeners;
    private final transient LobbyPayloadMapper payloadMapper;

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

    // --- GESTIONE LISTENER (Identica a Game) ---

    public void addListener(Listener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    // --- METODI PUBBLICI (Wrapper chiamati dai Controller) ---

    public boolean startMatch(String nickname) {
        // Passiamo null come target d'errore -> BROADCAST
        return this.executeLobbyAction(() -> this.startMatchInternal(nickname), null);
    }

    public boolean addPlayer(String nickname, Listener requesterView) {
        // Passiamo requesterView come target d'errore -> UNICAST a chi ha sbagliato
        return this.executeLobbyAction(() -> {
            this.addPlayerInternal(nickname);
            this.addListener(requesterView);
        }, requesterView);
    }

    public boolean removePlayer(String nickname, Listener requesterView) {
        // Passiamo null come target d'errore -> BROADCAST
        // (Nota: requesterView ci serve comunque per rimuoverlo dalla lista se ha successo!)
        return this.executeLobbyAction(() -> {
            this.removePlayerInternal(nickname);
            this.removeListener(requesterView);
        }, null);
    }

    public boolean rejoinResumablePlayer(String nickname, Listener requesterView) {
        return this.executeLobbyAction(() -> {
            this.rejoinResumablePlayerInternal(nickname);
            this.addListener(requesterView);
        }, requesterView);
    }

    public void reportPhaseError(Listener target) {
        if (target != null) {
            target.notifyUpdate(new ErrorPayload("PHASE_ERROR", "Action non permitted in this phase."));
        }
    }

    public void notifyPlayerJoined(String nickname) {
        this.notifyListeners(this.payloadMapper.lobbyJoined(nickname, this));
    }

    public void notifyPlayerLeft(String nickname, Listener leavingListener) {
        Object payload = this.payloadMapper.lobbyLeft(nickname, this, this.id);
        if (leavingListener != null) {
            leavingListener.notifyUpdate(payload);
        }
        this.notifyListeners(payload);
    }

    public void notifyMatchStarted(String nickname, Listener target) {
        if (target != null) {
            target.notifyUpdate(this.payloadMapper.lobbyStarted(nickname, this));
        }
    }


    // --- METODI PRIVATI (Logica interna e Check di dominio) ---

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

    private void removePlayerInternal(String nickname) {
        String validatedNickname = validateNickname(nickname);
        boolean removed = this.players.remove(validatedNickname);
        if (!removed) {
            throw new IllegalStateException("User not present in lobby: " + validatedNickname);
        }

        // Riassegnazione host
        if (validatedNickname.equals(this.hostNickname)) {
            this.hostNickname = this.players.isEmpty() ? null : this.players.get(0);
        }

        // Aggiornamento stato
        if (this.isEmpty()) {
            this.status = LobbyStatus.CLOSED;
        } else if (!this.canStart() && this.status == LobbyStatus.READY) {
            this.status = LobbyStatus.WAITING;
        }
    }

    // --- MOTORE CENTRALE DI ESECUZIONE ---

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

    private void notifyListeners(Object payload) {
        List<Listener> currentListeners = List.copyOf(this.listeners);
        for (Listener listener : currentListeners) {
            listener.notifyUpdate(payload);
        }
    }

    public void clearListeners() {
        this.listeners.clear();
    }
    // --- UTILITY E GETTERS ---

    private static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("User nickname cannot be null or blank");
        }
        return nickname;
    }

    public int getId() { return this.id; }
    public String getHostNickname() { return this.hostNickname; }
    public List<String> getPlayers() { return List.copyOf(this.players); }
    public int getChosenCapacity() { return this.capacity; }
    public LobbyStatus getStatus() { return this.status; }
    public boolean hasPlayer(String nickname) { return this.players.contains(validateNickname(nickname)); }
    public boolean isExpectedResumePlayer(String nickname) { return this.resumableRoster.contains(validateNickname(nickname)); }
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
    public boolean isHost(String nickname) { return this.hostNickname != null && this.hostNickname.equals(nickname); }
    public boolean canStart() { return this.players.size() == this.capacity; }
    public boolean isFull() { return this.players.size() >= this.capacity; }
    public boolean isEmpty() { return this.players.isEmpty(); }
}
