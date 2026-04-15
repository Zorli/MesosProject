package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerTCP;
import it.polimi.ingsoft.mesos.client.view.gui.ClientMain;
import it.polimi.ingsoft.mesos.client.view.tui.TuiController;

import java.io.IOException;
import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 */
public class LaunchClient {
    private static final String DEFAULT_TCP_HOST = "localhost";
    private static final int DEFAULT_TCP_PORT = 12_345;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Select UI mode:");
        System.out.println("1) GUI");
        System.out.println("2) TUI\n");
        String uiChoice = scanner.nextLine().trim();

        System.out.println("Select network technology:");
        System.out.println("1) TCP");
        System.out.println("2) RMI");
        String networkChoice = scanner.nextLine().trim();

        if (uiChoice.equals("1")) {
            ClientMain.main(args);
        } else {
            if (!networkChoice.equals("1")) {
                System.out.println("Only TUI + TCP is wired right now.");
                return;
            }

            try {
                TuiController tuiController = new TuiController();
                VirtualServerTCP virtualServerTCP = new VirtualServerTCP(
                        DEFAULT_TCP_HOST,
                        DEFAULT_TCP_PORT,
                        tuiController
                );
                tuiController.attachServerProxy(virtualServerTCP);
                tuiController.start();
            } catch (IOException exception) {
                throw new RuntimeException("Failed to connect to TCP server", exception);
            }
        }
    }
}
