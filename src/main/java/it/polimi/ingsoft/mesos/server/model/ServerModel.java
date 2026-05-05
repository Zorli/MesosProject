package it.polimi.ingsoft.mesos.server.model;

import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.server.controller.PreMatchPayloadMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// questo è lato model del pre_lobby, è singleton e fa unicast su tutti i listener (tiene tutti i listener e tutte le lobby grazie a lobbyRegistry)
public class ServerModel {
//    private final LobbyRegistry lobbyRegistry;
    // Serve per leggere i nickname e controllare se un utente è già in una fase avanzata
    private final ServerSessionRegistry sessions;
    private final PreMatchPayloadMapper payloadMapper;

    // Mappa globale dei Listener (Livello 1 - Tutte le VirtualView connesse)
    private final Map<String, Listener> globalListeners = new ConcurrentHashMap<>();

    public ServerModel(ServerSessionRegistry sessions) {
        this.sessions = sessions;
        this.payloadMapper = new PreMatchPayloadMapper();
    }


    public void addGlobalListener(String sessionId, Listener listener) {
        this.globalListeners.put(sessionId, listener);
    }

    public void removeGlobalListener(String sessionId) {
        this.globalListeners.remove(sessionId);
    }

    private void sendUnicastError(String sessionId, String errorType, String message) {
        Listener listener = this.globalListeners.get(sessionId);
        if (listener != null) {
            listener.notifyUpdate(new ErrorPayload(errorType, message));
        }
    }

    public void reportPhaseError(String sessionId, String actionName) {
        this.sendUnicastError(sessionId, "PHASE_ERROR", "Action '" + actionName + "' non permitted in this phase.");
    }

    // --- METODI DI BUSINESS (Fase PRE-LOBBY) ---

    public User authenticate(String sessionId, String nickname) {
        try {
            User user = this.sessions.authenticate(sessionId, nickname);
            Listener listener = this.globalListeners.get(sessionId);
            if (listener != null) {
                listener.notifyUpdate(this.payloadMapper.authenticated(nickname));
            }
            return user;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Unicast di ERRORE puro dal Model
            this.sendUnicastError(sessionId, "AUTH_ERROR", e.getMessage());
            return null;
        }
    }

    public List<Lobby> listOpenLobbies(String sessionId) {
        try {
            List<Lobby> openLobbies = this.sessions.getOpenLobbies();
            Listener listener = this.globalListeners.get(sessionId);
            if (listener != null) {
                listener.notifyUpdate(this.payloadMapper.lobbyList(openLobbies));
            }
            return openLobbies;
        } catch (Exception e) {
            this.sendUnicastError(sessionId, "LIST_LOBBY_ERROR", "Errore nel recupero delle lobby.");
            return null;
        }
    }

    public Lobby createLobby(String sessionId, int chosenCapacity) {
        try {
            this.ensureSessionCanEnterLobby(sessionId);
            String nickname = this.requireNickname(sessionId);

            Lobby lobby = this.sessions.createLobbyModel(nickname, chosenCapacity);

            // Unicast di Successo (Lobby creata)
            Listener listener = this.globalListeners.get(sessionId);
            if (listener != null) {
                lobby.addListener(listener);
                listener.notifyUpdate(this.payloadMapper.lobbyCreated(nickname, lobby));
            }
            return lobby; // Il Controller lo userà per fare: sessions.enterLobby()

        } catch (IllegalArgumentException | IllegalStateException e) {
            this.sendUnicastError(sessionId, "CREATE_LOBBY_ERROR", e.getMessage());
            return null;
        }
    }

    public Lobby joinLobby(String sessionId, int lobbyId) {
        try {
            this.ensureSessionCanEnterLobby(sessionId);
            String nickname = this.requireNickname(sessionId);
            Lobby lobby = this.sessions.requireLobbyModel(lobbyId);
            Listener listener = this.globalListeners.get(sessionId);
            if (listener != null) {
                lobby.addListener(listener);
            }

            if (!lobby.addPlayer(nickname)) {
                return null;
            }
            if (listener != null) {
                listener.notifyUpdate(this.payloadMapper.lobbyJoined(nickname, lobby));
            }
            // Nota: Qui il broadcast alla lobby avverrà o tramite il LobbyController
            // o direttamente notificando la lista interna della classe Lobby!

            return lobby; // Ritorna l'oggetto così il Controller fa sessions.enterLobby() e lo passa al LobbyController

        } catch (IllegalArgumentException | IllegalStateException e) {
            this.sendUnicastError(sessionId, "JOIN_LOBBY_ERROR", e.getMessage());
            return null;
        }
    }

    // --- CHECK DI DOMINIO PRIVATI ---

    private void ensureSessionCanEnterLobby(String sessionId) {
        if (this.sessions.lobbyIdOf(sessionId) != null) {
            throw new IllegalStateException("Already in a lobby.");
        }
        //??
        String nickname = this.requireNickname(sessionId);
        if (this.sessions.isPlayerInAnyLobby(nickname)) {
            throw new IllegalStateException("you're nickname is alredy in a lobby.");
        }
    }

    private String requireNickname(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalStateException("Must first authenticate with nickname.");
        }
        return nickname;
    }
}
