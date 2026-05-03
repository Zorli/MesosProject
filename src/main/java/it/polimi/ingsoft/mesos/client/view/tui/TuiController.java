package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
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

    /*
    Analisi dei thread che stanno girando nel caso del tuicontroller con le due tech di rete:

    Caso TCP.
    1. Main thread su cui gira tuicontroller
    2. Thread su cui gira virtualserver con loop di lettura messaggi da server

    Caso RMI:
    1. Thread del controller
    2. Thread RMI

    Abbiamo sempre quindi due soli thread, è quindi molto importante che i metodi per plottare a schermo siano thread-safe

     */
    public void start() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("serverProxy must be attached before starting the TUI");
        }

        this.render("Mesos TUI started. Type 'help' to list commands.");

        while (this.scanner.hasNextLine()) {
            String line = this.scanner.nextLine().trim();

            if (line.isBlank()) {
                this.renderPrompt();
                continue;
            }

            if (this.handleInputLine(line)) {
                return;
            }
        }

        //In ogni caso faccio close
        this.closeServerProxy();
    }

    private boolean handleInputLine(String line) {
        try {
            TuiCommand command = this.commandParser.parse(line);
            return this.executeCommand(command);
        } catch (IllegalArgumentException exception) {
            this.showError(new ErrorPayload("CLIENT_COMMAND_ERROR", exception.getMessage()));
            return false;
        } catch (IOException exception) {
            this.showError(new ErrorPayload("CONNECTION_CLOSED", exception.getMessage()));
            return true;
        } catch (UncheckedIOException exception) {
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
                this.render("Client closed.");
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




    //Metodi per plottare
    @Override
    public synchronized void showError(ErrorPayload payload) {
        this.render(this.payloadRenderer.renderError(payload, this.lastGamePayload));
    }

    @Override
    public synchronized void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        this.render(this.payloadRenderer.renderPreMatchUpdate(payload));
    }

    @Override
    public synchronized void showLobbyList(LobbyListPayload payload) {
        this.render(this.payloadRenderer.renderLobbyList(payload));
    }

    @Override
    public synchronized void showGameUpdate(GamePayload payload) {
        this.lastGamePayload = payload;
        this.render(this.payloadRenderer.renderGame(payload));
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
