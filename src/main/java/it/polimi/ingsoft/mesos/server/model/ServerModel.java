package it.polimi.ingsoft.mesos.server.model;

import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
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

    public void reportPhaseError(String sessionId) {
        this.sendUnicastError(sessionId, "PHASE_ERROR", "Action non permitted in this phase.");
    }

    public void reportJoinError(String sessionId, String message) {
        this.sendUnicastError(sessionId, "JOIN_ERROR", message);
    }

    public void reportRoutingError(String sessionId, String message) {
        this.sendUnicastError(sessionId, "ROUTING_ERROR", message);
    }

    // --- METODI DI BUSINESS (Fase PRE-LOBBY) ---

    public void authenticate(String sessionId, String nickname, Listener clientView) {
        try {
            this.sessions.authenticate(sessionId, nickname);

            clientView.notifyUpdate(this.payloadMapper.authenticated(nickname));

        } catch (IllegalArgumentException | IllegalStateException e) {
            clientView.notifyUpdate(new ErrorPayload("AUTH_ERROR", e.getMessage()));
        }
    }

    public void listOpenLobbies(String sessionId, Listener clientView) {
        try {
            List<Lobby> openLobbies = this.sessions.getOpenLobbies();
            clientView.notifyUpdate(this.payloadMapper.lobbyList(openLobbies));

        } catch (Exception e) {
            clientView.notifyUpdate(new ErrorPayload("LIST_OPEN_ERROR", e.getMessage()));
        }
    }

    public Lobby createLobby(String sessionId, int chosenCapacity, Listener clientView) {
        try {
            this.ensureSessionCanEnterLobby(sessionId);
            String nickname = this.requireNickname(sessionId);
            Lobby lobby = this.sessions.createLobbyModel(nickname, chosenCapacity);

            lobby.addListener(clientView);
            clientView.notifyUpdate(this.payloadMapper.lobbyCreated(nickname, lobby));

            return lobby;

        } catch (IllegalArgumentException | IllegalStateException e) {
            clientView.notifyUpdate(new ErrorPayload("CREATE_LOBBY_ERROR", e.getMessage()));
            return null;
        }
    }

    public boolean joinLobbyValidation(String sessionId, Listener clientView, int lobbyId) {
        try {
            this.ensureSessionCanEnterLobby(sessionId);

            if (this.sessions.getLobbyModel(lobbyId).isEmpty()) {
                throw new IllegalArgumentException("La lobby richiesta non esiste.");
            }
            return true;
        } catch (Exception e) {
            // UNICAST DI ERRORE DAL MODEL (MVC Puro)
            clientView.notifyUpdate(new ErrorPayload("JOIN_ERROR" , e.getMessage()));
            return false;
        }
    }

    // --- CHECK DI DOMINIO PRIVATI ---

    private void ensureSessionCanEnterLobby(String sessionId) {
        if (this.sessions.lobbyIdOf(sessionId) != null) {
            throw new IllegalStateException("Already in a lobby.");
        }
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
