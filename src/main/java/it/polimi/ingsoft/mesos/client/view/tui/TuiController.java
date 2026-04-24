package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

/**
 * State-driven terminal controller for the Mesos client.
 */
public class TuiController implements ClientView {
    private final Scanner scanner;
    private final TuiTerminal terminal;
    private final TuiStateReducer stateReducer;
    private final TuiRenderer renderer;
    private final TuiCommandRouter commandRouter;

    private TuiState state;
    private ServerProxy serverProxy;

    /**
     * Creates the default interactive TUI bound to standard input and output.
     */
    public TuiController() {
        this(new Scanner(System.in), System.out, true);
    }

    /**
     * Creates a TUI with injectable terminal collaborators.
     *
     * @param scanner terminal scanner
     * @param out terminal output stream
     * @param clearScreenEnabled whether redraws should clear the terminal
     */
    public TuiController(Scanner scanner, PrintStream out, boolean clearScreenEnabled) {
        this.scanner = Objects.requireNonNull(scanner, "scanner cannot be null");
        this.terminal = new TuiTerminal(Objects.requireNonNull(out, "out cannot be null"), clearScreenEnabled);
        this.state = TuiState.initial();
        this.stateReducer = new TuiStateReducer();
        this.renderer = new TuiRenderer();
        this.commandRouter = new TuiCommandRouter();
    }

    /**
     * Attaches the transport-agnostic server proxy used by this controller.
     *
     * @param serverProxy server proxy instance
     */
    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
        this.replaceState(this.stateReducer.reduceConnection(this.state, serverProxy.isConnected()));
    }

    /**
     * Starts the interactive command loop.
     */
    public void start() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("serverProxy must be attached before starting the TUI");
        }
        this.syncConnectionState();
        this.renderState("Mesos TUI started.");

        while (this.scanner.hasNextLine()) {
            TuiCommand command;
            try {
                command = TuiCommandParser.parse(this.scanner.nextLine());
            } catch (IllegalArgumentException exception) {
                this.printMessage("[CLIENT][ERROR] " + exception.getMessage());
                continue;
            }

            try {
                if (this.handleCommand(command)) {
                    return;
                }
            } catch (Exception exception) {
                this.printMessage("[CLIENT][ERROR] " + exception.getMessage());
            }
        }
    }

    /**
     * Applies one server error update and redraws the current state.
     *
     * @param payload error payload pushed by the server
     */
    @Override
    public void showError(ErrorPayload payload) {
        this.replaceState(this.stateReducer.reduceError(this.state, Objects.requireNonNull(payload, "payload cannot be null")));
        this.renderState("Server error received.");
    }

    /**
     * Applies one pre-match server update and redraws the current state.
     *
     * @param payload pre-match payload pushed by the server
     */
    @Override
    public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        this.replaceState(this.stateReducer.reducePreMatchUpdate(
                this.state,
                Objects.requireNonNull(payload, "payload cannot be null")
        ));
        this.renderState("Pre-match update received.");
    }

    /**
     * Applies one lobby-list server update and redraws the current state.
     *
     * @param payload lobby-list payload pushed by the server
     */
    @Override
    public void showLobbyList(LobbyListPayload payload) {
        this.replaceState(this.stateReducer.reduceLobbyList(
                this.state,
                Objects.requireNonNull(payload, "payload cannot be null")
        ));
        this.renderState("Lobby list received.");
    }

    /**
     * Applies one in-match game snapshot and redraws the current state.
     *
     * @param payload game snapshot pushed by the server
     */
    @Override
    public void showGameUpdate(GamePayload payload) {
        this.replaceState(this.stateReducer.reduceGameUpdate(
                this.state,
                Objects.requireNonNull(payload, "payload cannot be null")
        ));
        this.renderState("Game update received.");
    }

    /**
     * Replaces the current immutable state snapshot.
     *
     * @param newState new immutable state
     */
    private void replaceState(TuiState newState) {
        this.state = Objects.requireNonNull(newState, "newState cannot be null");
    }

    /**
     * Handles one parsed command.
     *
     * @param command parsed command
     * @return {@code true} when the controller should terminate
     * @throws IOException when closing the connection fails
     */
    private boolean handleCommand(TuiCommand command) throws IOException {
        this.syncConnectionState();
        TuiCommandRouter.RouteResult result = this.commandRouter.route(
                command,
                this.serverProxy,
                new TuiCommandRouter.LocalActions() {
                    @Override
                    public void showHelp() {
                        replaceState(stateReducer.reduceShowHelp(state));
                        renderState(null);
                    }

                    @Override
                    public void showStatus() {
                        replaceState(stateReducer.reduceShowStatus(state));
                        renderState(null);
                    }
                }
        );

        if (result.feedbackMessage() != null) {
            if (result.shouldExit()) {
                this.replaceState(this.stateReducer.reduceConnection(this.state, false));
                this.terminal.printMessage(result.feedbackMessage());
            } else {
                this.printMessage("[CLIENT] " + result.feedbackMessage());
            }
        } else if (result.promptOnly()) {
            this.terminal.prompt();
        }

        return result.shouldExit();
    }

    /**
     * Synchronizes the cached connection status with the current proxy state.
     */
    private void syncConnectionState() {
        if (this.serverProxy != null) {
            this.replaceState(this.stateReducer.reduceConnection(this.state, this.serverProxy.isConnected()));
        }
    }

    /**
     * Renders the current state.
     *
     * @param banner optional banner line shown above the rendered state
     */
    private void renderState(String banner) {
        this.terminal.render(this.renderer.render(this.state), true, banner);
    }

    /**
     * Prints one transient message.
     *
     * @param message message to print
     */
    private void printMessage(String message) {
        this.terminal.printMessage(message);
    }
}
