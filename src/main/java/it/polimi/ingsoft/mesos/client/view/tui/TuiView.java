package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Scanner;

/**
 * Text user interface controller for the client application.
 *
 * <p>This class has two architectural roles:
 * <ul>
 *     <li>it reads user commands from a {@link Scanner}, parses them with
 *     {@link TuiCommandParser}, and forwards them to the attached {@link ServerProxy};</li>
 *     <li>it implements {@link ClientView}, so the networking layer can call it when
 *     the server sends updates that must be printed on the terminal.</li>
 * </ul>
 *
 * <p>The TUI normally collaborates with two threads. The main client thread runs
 * {@link #start()} and reads user input. A networking thread may call the
 * {@link ClientView} methods when server messages arrive. With TCP, that second
 * thread is the {@code VirtualServerTCP} reader thread. With RMI, it is an RMI
 * callback thread.
 *
 * <p>For this reason, the methods that print output are synchronized: command
 * output and server updates must not write to the terminal at the same time.
 */
public class TuiView implements ClientView {
    private final Scanner scanner;
    private final PrintStream out;
    private final TuiCommandParser commandParser;
    private final TuiPayloadRenderer payloadRenderer;
    private GamePayload lastGamePayload;
    private String localNickname;
    private ServerProxy serverProxy;

    /**
     * Creates a TUI connected to the real standard input and standard output.
     */
    public TuiView() {
        this(new Scanner(System.in), System.out);
    }

    /**
     * Creates a TUI using explicit input and output streams.
     *
     * <p>This constructor is mainly useful in tests, where the input can be a
     * scripted {@link Scanner} and the output can be captured in memory.
     *
     * @param scanner source of user command lines
     * @param out destination used to print TUI output
     */
    public TuiView(Scanner scanner, PrintStream out) {
        this.scanner = Objects.requireNonNull(scanner, "scanner cannot be null");
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.commandParser = new TuiCommandParser();
        this.payloadRenderer = new TuiPayloadRenderer();
    }

    /**
     * Attaches the network-side proxy used to send user commands to the server.
     *
     * <p>The proxy must be attached before {@link #start()} is called.
     *
     * @param serverProxy proxy connected to the remote server
     */
    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    /**
     * Starts the blocking input loop of the text user interface.
     *
     * <p>The method prints the initial message, then reads one line at a time.
     * Blank lines only redraw the prompt. Non-blank lines are parsed and
     * dispatched to the server proxy. The loop stops when the user enters
     * {@code quit}, when a connection error happens, or when the scanner reaches
     * end of input.
     *
     * <p>If input ends without an explicit {@code quit}, the controller still
     * closes the attached server proxy.
     *
     * @throws IllegalStateException if no server proxy was attached before start
     */
    public void start() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("serverProxy must be attached before starting the TUI");
        }

        this.render(this.payloadRenderer.renderWelcome());

        while (this.scanner.hasNextLine()) {
            String line = this.scanner.nextLine().trim();

            if (line.isBlank()) {
                this.renderPrompt();
                continue;
            }

            // Clear the terminal as soon as the user submits a command so any
            // subsequent output (server update, error, help) lands on a fresh
            // screen instead of accumulating below the previous frame.
            this.clearScreen();

            if (this.handleInputLine(line)) {
                return;
            }
        }

        this.closeServerProxy();
    }

    private boolean handleInputLine(String line) {
        try {
            TuiCommand command = this.commandParser.parse(line);
            return this.executeCommand(command);
        } catch (IllegalArgumentException exception) {
            this.showError(new ErrorPayload("CLIENT_COMMAND_ERROR", exception.getMessage()));
            return false;
        } catch (IOException | UncheckedIOException exception) {
            this.showError(new ErrorPayload("CONNECTION_CLOSED", exception.getMessage()));
            return true;
        } catch (RuntimeException exception) {
            this.showError(new ErrorPayload("CLIENT_ERROR", exception.getMessage()));
            return false;
        }
    }

    private boolean executeCommand(TuiCommand command) throws IOException {
        switch (command.type()) {
            case HELP -> {
                this.renderHelp();
                return false;
            }
            case AUTH -> {
                this.serverProxy.authenticate(command.argument());
            }
            case LIST_LOBBIES -> {
                this.serverProxy.listLobbies();
            }
            case CREATE_LOBBY -> {
                this.serverProxy.createLobby(command.intArgument());
            }
            case JOIN_LOBBY -> {
                this.serverProxy.joinLobby(command.intArgument());
            }
            case QUIT_LOBBY -> {
                this.serverProxy.exitLobby();
            }
            case START_LOBBY -> {
                this.serverProxy.startLobby();
            }
            case MOVE_TOTEM -> {
                this.serverProxy.moveTotem(command.argument());
            }
            case CHOOSE_CARD -> {
                this.serverProxy.chooseCard(command.intArgument());
            }
            case EXTRA_DRAW -> {
                this.serverProxy.extraDraw(command.intArgument());
            }
            case QUIT -> {
                this.serverProxy.close();
                this.render(this.payloadRenderer.renderFarewell());
                return true;
            }
        }

        return false;
    }

    private void closeServerProxy() {
        try {
            this.serverProxy.close();
        } catch (IOException exception) {
            this.showError(new ErrorPayload("CONNECTION_CLOSED", exception.getMessage()));
        }
    }




    /**
     * Prints a server or client error.
     *
     * <p>If a game state was already received, the renderer prints it again after
     * the error so the player does not lose the current board context.
     *
     * @param payload error data to show
     */
    @Override
    public synchronized void showError(ErrorPayload payload) {
        this.render(this.payloadRenderer.renderError(payload, this.lastGamePayload, this.localNickname));
    }

    /**
     * Prints a successful authentication result received from the server.
     *
     * @param payload authenticated nickname and status message
     */
    @Override
    public synchronized void showAuthenticationResult(AuthenticationResultPayload payload) {
        this.localNickname = payload.nickname;
        this.render(this.payloadRenderer.renderAuthenticationResult(payload));
    }

    /**
     * Prints a lobby update received from the server.
     *
     * @param payload lobby update to show
     */
    @Override
    public synchronized void showLobbyUpdate(LobbyUpdatePayload payload) {
        this.render(this.payloadRenderer.renderLobbyUpdate(payload));
    }

    /**
     * Prints the list of open lobbies received from the server.
     *
     * @param payload lobby list to show
     */
    @Override
    public synchronized void showLobbyList(LobbyListPayload payload) {
        this.render(this.payloadRenderer.renderLobbyList(payload));
    }

    /**
     * Prints the current game state received from the server.
     *
     * <p>The payload is stored as the last known game state. If an error happens
     * during the match, {@link #showError(ErrorPayload)} can render this state
     * again after the error message.
     *
     * @param payload current game state to show
     */
    @Override
    public synchronized void showGameUpdate(GamePayload payload) {
        this.lastGamePayload = payload;
        this.render(this.payloadRenderer.renderGame(payload, this.localNickname));
    }

    /**
     * Prints the final game result and clears the stored game state.
     *
     * <p>After the final result, the match is over, so errors should no longer
     * reprint the old board.
     *
     * @param payload final game state to show
     */
    @Override
    public synchronized void showFinalGameResult(GamePayload payload) {
        this.lastGamePayload = null;
        this.render(this.payloadRenderer.renderFinalGameResult(payload));
    }

    private void renderHelp() {
        this.render(this.payloadRenderer.renderHelp());
    }


    private synchronized void render(String message) {
        this.out.println();
        this.out.println(message);
        this.printPrompt();
    }

    private synchronized void renderPrompt() {
        this.printPrompt();
    }

    private void printPrompt() {
        this.out.print("> ");
        this.out.flush();
    }

    private synchronized void clearScreen() {
        this.out.print(TuiStyle.CLEAR_SCREEN);
        this.out.flush();
    }
}
