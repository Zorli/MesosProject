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

public class TuiController implements ClientView {
    private final Scanner scanner;
    private final PrintStream out;
    private final TuiCommandParser commandParser;
    private final TuiPayloadRenderer payloadRenderer;
    private GamePayload lastGamePayload;
    private LobbyListPayload lastLobbyListPayload;
    private PreMatchUpdatePayload lastPreMatchUpdatePayload;
    private ServerProxy serverProxy;

    public TuiController() {
        this(new Scanner(System.in), System.out);
    }

    public TuiController(Scanner scanner, PrintStream out) {
        this.scanner = Objects.requireNonNull(scanner, "scanner cannot be null");
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.commandParser = new TuiCommandParser();
        this.payloadRenderer = new TuiPayloadRenderer();
    }

    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

//    abbiamo sempre due soli thread che girano
//    Caso TCP:
//    Thread TUI/input: scanner.nextLine() -> commandParser.parse() -> executeCommand() -> serverProxy.method()
//    Thread trasporto: payload -> clientView.showGameUpdate -> render
//    Caso RMI:
//    Thread input TUI:
//    scanner.nextLine() -> commandParser.parse() -> executeCommand() -> serverProxyRMI.method() -> chiamata RMI al server
//
//    Thread RMI callback:
//   server -> VirtualServerRMI.handleGameUpdate() -> clientView.showGameUpdate() -> TuiController.render()
//    Quindi se abbiamo un unico metodo di render synchronized siamo safe lato concorrenza di stampa.

    public void start() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("serverProxy must be attached before starting the TUI");
        }

        this.render("Mesos TUI started. Type 'help' to list commands.");
        while (this.scanner.hasNextLine()) {
            String commandLine = this.scanner.nextLine().trim();
            if (commandLine.isBlank()) {
                this.renderPrompt();
                continue;
            }

            try {
                TuiCommand command = this.commandParser.parse(commandLine);
                if (this.executeCommand(command)) {
                    return;
                }
            } catch (Exception exception) {
                this.render("[CLIENT][ERROR] " + exception.getMessage());
            }
        }
        // When the TUI terminates, close the attached server proxy.
        // For TCP this closes socket/streams and lets the receiver thread terminate.
        // For RMI this closes the remote session and unexports the callback object.
        if(this.serverProxy != null) {
            try {
                this.serverProxy.close();
            } catch (IOException e) {
               this.render("[CLIENT][ERROR] " + e.getMessage());
            }
        }


    }

    @Override
    public synchronized void showError(ErrorPayload payload) {
        this.render(this.payloadRenderer.renderError(payload, this.lastGamePayload));
    }

    @Override
    public synchronized void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        this.lastPreMatchUpdatePayload = payload;
        this.render(this.payloadRenderer.renderPreMatchUpdate(payload));
    }

    @Override
    public synchronized void showLobbyList(LobbyListPayload payload) {
        this.lastLobbyListPayload = payload;
        this.render(this.payloadRenderer.renderLobbyList(payload));
    }

    @Override
    public synchronized void showGameUpdate(GamePayload payload) {
        this.lastGamePayload = payload;
        this.render(this.payloadRenderer.renderGame(payload));
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
                this.render("Client closed.");
                return true;
            }
        }

        return false;
    }

    private void renderHelp() {
        this.render("""
                General:
                help
                auth <nickname>
                quit

                Lobby:
                list-lobbies
                create-lobby <capacity>
                join-lobby <lobby-id>
                quit-lobby
                start-lobby

                Match:
                move-totem <tile>
                choose-card <card-id>
                extra-draw <card-id>

                Aliases:
                exit -> quit
                lobbies -> list-lobbies
                create -> create-lobby
                join -> join-lobby
                """);
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
}
