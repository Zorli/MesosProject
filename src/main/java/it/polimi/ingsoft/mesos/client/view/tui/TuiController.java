package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerMessageListener;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServer;

import java.util.Objects;
import java.util.Scanner;

/**
 * Text-based user interface controller for the Mesos client.
 * Handles terminal input parsing and output rendering.
 */
public class TuiController implements ServerMessageListener {
    private VirtualServer virtualServer;

    public void attachVirtualServer(VirtualServer virtualServer) {
        this.virtualServer = Objects.requireNonNull(virtualServer, "virtualServer cannot be null");
    }

    public void start() {
        if (this.virtualServer == null) {
            throw new IllegalStateException("virtualServer must be attached before starting the TUI");
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("Mesos TUI started.");
        System.out.println("TCP connected: " + this.virtualServer.isConnected());
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
                    case "raw" -> this.handleRawMessage(commandLine);
                    case "quit" -> {
                        this.virtualServer.close();
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
        System.out.println("  raw <json>");
        System.out.println("  quit");
    }

    private void handleAuthentication(String commandLine) throws Exception {
        String nickname = this.extractArgument(commandLine, "auth");
        this.virtualServer.authenticate(nickname);
        System.out.println("Authentication request sent.");
    }

    private void handleRawMessage(String commandLine) throws Exception {
        String rawMessage = this.extractArgument(commandLine, "raw");
        this.virtualServer.sendRaw(rawMessage);
        System.out.println("Raw message sent.");
    }

    private String extractArgument(String commandLine, String command) {
        String prefix = command + " ";
        if (!commandLine.startsWith(prefix) || commandLine.length() <= prefix.length()) {
            throw new IllegalArgumentException("Usage: " + command + " <argument>");
        }
        return commandLine.substring(prefix.length()).trim();
    }

    private synchronized void printPrompt() {
        System.out.print("> ");
        System.out.flush();
    }

    @Override
    public synchronized void onServerMessage(String message) {
        System.out.println();
        System.out.println(message);
        this.printPrompt();
    }
}
