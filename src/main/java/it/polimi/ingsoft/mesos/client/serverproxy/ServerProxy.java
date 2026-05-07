package it.polimi.ingsoft.mesos.client.serverproxy;

import java.io.IOException;

// Here we expose the services that the client-side controllers can request to the server
// without depending on the concrete transport technology.
// Unlike VirtualView, which defines the remote contract exposed by the server to the client,
// this interface is an internal client-side contract used by TUI/GUI to ask for server services.
// TCP and RMI client proxies should implement this interface so controllers can stay transport-agnostic.
//Something similar is used in the ClientView interface, a client-side contract that
//is used to maintain stable API for the network to contact the user interface indipendently from the
//fact that we have a textual interface or a graphical inteface
public interface ServerProxy extends AutoCloseable {
    boolean isConnected();

    void authenticate(String nickname);

    void listLobbies();

    void createLobby(int chosenCapacity);

    void joinLobby(int lobbyId);

    void exitLobby();

    void startLobby();

    void moveTotem(String tile);

    void chooseCard(int cardId);

    void extraDraw(int cardId);

    @Override
    void close() throws IOException;
}
