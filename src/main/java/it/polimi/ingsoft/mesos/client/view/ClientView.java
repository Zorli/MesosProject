package it.polimi.ingsoft.mesos.client.view;

import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServer;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;

/**
 * Client-side output contract.
 *
 * <p>The networking layer does not know whether the user is using the text
 * interface or the graphical interface. It(VirtualServers,{@link VirtualServer}) only receives server-to-client
 * payloads and forwards them to a {@code ClientView}. Each implementation then
 * decides how those payloads must be shown to the user.
 *
 * <p>In the current client architecture this interface is used by
 * {@link MessageRouter}: the router reads the payload type and calls the method
 * that matches that type. {@code TuiView} prints the payloads as text,
 * while {@code GUIView} can render the same information graphically.
 */
public interface ClientView {

    /**
     * Shows an error received from the server or produced locally by the client.
     *
     * @param payload error code and human-readable error message
     */
    void showError(ErrorPayload payload);

    /**
     * Shows a successful authentication result before the client joins a lobby.
     *
     * @param payload authenticated nickname and status message
     */
    void showAuthenticationResult(AuthenticationResultPayload payload);

    /**
     * Shows the current state of the lobby joined by the client.
     *
     * @param payload lobby identifier, lobby players, capacity, and lobby state
     */
    void showLobbyUpdate(LobbyUpdatePayload payload);

    /**
     * Shows the list of lobbies currently available before joining a match.
     *
     * @param payload available lobby summaries
     */
    void showLobbyList(LobbyListPayload payload);

    /**
     * Shows a normal in-game update.
     *
     * @param payload complete game state visible to the client
     */
    void showGameUpdate(GamePayload payload);

    /**
     * Shows the final game result.
     *
     * @param payload complete final game state, including winner information
     */
    void showFinalGameResult(GamePayload payload);
}
