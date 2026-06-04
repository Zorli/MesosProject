package it.polimi.ingsoft.mesos.client.serverproxy;

import java.io.IOException;

/**
 * Client-side contract used by the user interface to send commands to the server.
 * <p>
 * The UI must not know if the client is connected through TCP or RMI. For this reason
 * both {@code ServerProxyTCP} and {@code ServerProxyRMI} implement this interface and
 * expose the same application-level operations.
 * <p>
 * This interface represents the outgoing direction of the client architecture:
 *
 * <pre>
 * UI controller -> ServerProxy -> server
 * </pre>
 *
 * The opposite direction is handled by {@code VirtualServer}, which receives messages
 * coming from the server and forwards them to the client view.
 */
public interface ServerProxy extends AutoCloseable {
    /**
     * Returns whether the proxy currently considers the connection to the server active.
     *
     * @return {@code true} if the proxy is connected, {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Sends an authentication request with the nickname chosen by the user.
     *
     * @param nickname nickname chosen by the user
     */
    void authenticate(String nickname);

    /**
     * Requests the list of currently available lobbies.
     */
    void listLobbies();

    /**
     * Requests the creation of a new lobby with the given capacity.
     *
     * @param chosenCapacity number of players expected in the lobby
     */
    void createLobby(int chosenCapacity);

    /**
     * Requests to join an existing lobby.
     *
     * @param lobbyId identifier of the lobby to join
     */
    void joinLobby(int lobbyId);

    /**
     * Requests to leave the current lobby.
     */
    void exitLobby();

    /**
     * Requests to start the current lobby.
     */
    void startLobby();

    /**
     * Sends the tile selected by the user for the totem movement.
     *
     * @param tile identifier of the selected tile
     */
    void moveTotem(String tile);

    /**
     * Sends the card selected by the user during the normal card choice phase.
     *
     * @param cardId identifier of the selected card
     */
    void chooseCard(int cardId);

    /**
     * Sends the card selected by the user during the extra draw phase.
     *
     * @param cardId identifier of the selected card
     */
    void extraDraw(int cardId);



    default void skipExtraDraw() {
        // Default implementation does nothing, as not all proxies may support this operation.
    }

    /**
     * Closes the resources owned by the concrete proxy implementation.
     *
     * @throws IOException if the concrete transport fails while closing
     */
    @Override
    void close() throws IOException;
}
