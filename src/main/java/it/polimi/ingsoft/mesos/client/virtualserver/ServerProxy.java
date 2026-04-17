package it.polimi.ingsoft.mesos.client.virtualserver;

import java.io.IOException;

// Here we expose the services that the client-side controllers can request to the server
// without depending on the concrete transport technology.
// Unlike VirtualView, which defines the remote contract exposed by the server to the client,
// this interface is an internal client-side contract used by TUI/GUI to ask for server services.
// TCP and RMI client proxies should implement this interface so controllers can stay transport-agnostic.
public interface ServerProxy extends AutoCloseable {
    boolean isConnected();

    boolean authenticate(String nickname);

    boolean listLobbies();

    boolean createLobby(int chosenCapacity);

    boolean joinLobby(int lobbyId);

    boolean exitLobby(int lobbyId);

    @Override
    void close() throws IOException;
}
