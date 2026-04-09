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
        while (true) {
            this.printMenu();
            System.out.print("Select command: ");
            if (!scanner.hasNextLine()) {
                return;
            }

            String command = scanner.nextLine().trim();
            try {
                switch (command) {
                    case "1" -> this.handleAuthentication(scanner);
                    case "2" -> this.handleRawMessage(scanner);
                    case "0" -> {
                        this.virtualServer.close();
                        System.out.println("Client closed.");
                        return;
                    }
                    default -> System.out.println("Unsupported command.");
                }
            } catch (Exception exception) {
                System.out.println("Client error: " + exception.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("1) authenticate");
        System.out.println("2) send raw json");
        System.out.println("0) quit");
    }

    private void handleAuthentication(Scanner scanner) throws Exception {
        System.out.print("Nickname: ");
        String nickname = scanner.nextLine().trim();
        this.virtualServer.authenticate(nickname);
        System.out.println("Authentication request sent.");
    }

    private void handleRawMessage(Scanner scanner) throws Exception {
        System.out.print("Raw JSON: ");
        String rawMessage = scanner.nextLine();
        this.virtualServer.sendRaw(rawMessage);
        System.out.println("Raw message sent.");
    }

    @Override
    public synchronized void onServerMessage(String message) {
        System.out.println();
        System.out.println(message);
    }
}
