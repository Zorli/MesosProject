package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.util.Objects;
import java.util.Scanner;

/**
 * Text-based user interface controller for the Mesos client.
 * Handles terminal input parsing and output rendering.
 */
public class TuiController implements ClientView {
    private ServerProxy serverProxy;

    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    public void start() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("serverProxy must be attached before starting the TUI");
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("Mesos TUI started.");
        System.out.println("TCP connected: " + this.serverProxy.isConnected());
        System.out.println("Type 'help' to list commands.");
        this.printPrompt();
        while (true) {
            if (!scanner.hasNextLine()) {
                return;
            }

            String commandLine = scanner.nextLine().trim();
            try {
                switch (this.readCommand(commandLine)) {
                    case "help" -> this.printHelp();
                    case "auth" -> this.handleAuthentication(commandLine);
                    case "list-lobbies" -> this.handleListLobbies();
                    case "create-lobby" -> this.handleCreateLobby(commandLine);
                    case "join-lobby" -> this.handleJoinLobby(commandLine);
                    case "quit-lobby" -> this.handleExitLobby(commandLine);

                    case "quit" -> {
                        this.serverProxy.close();
                        System.out.println("Client closed.");
                        return;
                    }
                    default -> System.out.println("Unsupported command.");
                }
            } catch (Exception exception) {
                System.out.println("Client error: " + exception.getMessage());
            }
            this.printPrompt();
        }
    }

    private String readCommand(String commandLine) {
        if (commandLine.isBlank()) {
            return "";
        }
        return commandLine.split("\\s+", 2)[0].toLowerCase();
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  help");
        System.out.println("  auth <nickname>");
        System.out.println("  list-lobbies");
        System.out.println("  create-lobby <capacity>");
        System.out.println("  join-lobby <lobby-id>");
        System.out.println("  quit-lobby <lobby-id>");
        System.out.println("  start-lobby <lobby-id>");
//        System.out.println("  stop-lobbys"); //If he host quit a lobby what happens to the other player in the lobby?




        System.out.println("  quit");
    }


    //Methods to handle the Commands call, forward to the virtualserver!-----
    private void handleAuthentication(String commandLine) throws Exception {
        String nickname = this.extractArgument(commandLine, "auth");
        this.serverProxy.authenticate(nickname);
        System.out.println("Authentication request sent.");
    }

    private void handleListLobbies() {
        this.serverProxy.listLobbies();
        System.out.println("List-lobbies request sent.");
    }

    private void handleCreateLobby(String commandLine) {
        int chosenCapacity = this.extractIntArgument(commandLine, "create-lobby");
        this.serverProxy.createLobby(chosenCapacity);
        System.out.println("Create-lobby request sent.");
    }

    private void handleJoinLobby(String commandLine) {
        int lobbyId = this.extractIntArgument(commandLine, "join-lobby");
        this.serverProxy.joinLobby(lobbyId);
        System.out.println("Join-lobby request sent.");
    }

    private void handleExitLobby(String commandLine) {
        int lobbyId = this.extractIntArgument(commandLine, "quit-lobby");
        this.serverProxy.exitLobby(lobbyId);
        System.out.println("Quit-lobby request sent.");
    }

    //Helper to parse-----
    private String extractArgument(String commandLine, String command) {
        String prefix = command + " ";
        if (!commandLine.startsWith(prefix) || commandLine.length() <= prefix.length()) {
            throw new IllegalArgumentException("Usage: " + command + " <argument>");
        }
        return commandLine.substring(prefix.length()).trim();
    }

    private int extractIntArgument(String commandLine, String command) {
        String argument = this.extractArgument(commandLine, command);
        try {
            return Integer.parseInt(argument);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Usage: " + command + " <integer>");
        }
    }





    //I/O methods -----
    private synchronized void printPrompt() {
        System.out.print("> ");
        System.out.flush();
    }

    @Override
    public synchronized void showError(ErrorPayload payload) {
        System.out.println();
        System.out.println("[SERVER][ERROR] " + payload.code + ": " + payload.message);
        this.printPrompt();
    }

    @Override
    public synchronized void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        StringBuilder messageBuilder = new StringBuilder("[SERVER][PRE-MATCH] ")
                .append(payload.event);

        if (payload.nickname != null) {
            messageBuilder.append(" nickname=").append(payload.nickname);
        }
        if (payload.message != null) {
            messageBuilder.append(" message=").append(payload.message);
        }
        if (payload.lobbyId != null) {
            messageBuilder.append(" lobbyId=").append(payload.lobbyId);
        }
        if (payload.hostNickname != null) {
            messageBuilder.append(" host=").append(payload.hostNickname);
        }
        if (payload.maxPlayers != null) {
            messageBuilder.append(" maxPlayers=").append(payload.maxPlayers);
        }
        if (payload.players != null && !payload.players.isEmpty()) {
            messageBuilder.append(" players=").append(payload.players);
        }

        System.out.println();
        System.out.println(messageBuilder);
        this.printPrompt();
    }

    @Override
    public synchronized void showLobbyList(LobbyListPayload payload) {
        System.out.println();
        System.out.println("[SERVER][LOBBY-LIST]");
        if (payload == null || payload.lobbies == null || payload.lobbies.isEmpty()) {
            System.out.println("No open lobbies.");
            this.printPrompt();
            return;
        }

        for (LobbySummaryPayload lobby : payload.lobbies) {
            System.out.println(
                    "lobbyId=" + lobby.lobbyId
                            + " host=" + lobby.hostNickname
                            + " players=" + lobby.currentPlayers + "/" + lobby.maxPlayers
                            + " status=" + lobby.status
            );
        }
        this.printPrompt();
    }

    @Override
    public synchronized void showGameUpdate(GamePayload payload) {
        System.out.println();
        System.out.println("[SERVER][GAME] update received for gameId=" + payload.id);
        this.printPrompt();
    }
}
