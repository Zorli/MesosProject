package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.view.gui.GUIController;
import it.polimi.ingsoft.mesos.client.view.tui.TuiController;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;

import java.rmi.Naming;
import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 */
public class LaunchClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    // Nota su architettura del client
    // La UI può essere TUI o GUI
    // La rete può essere TCP o RMI
    // Per rendere intercambiabili queste scelte servono contratti chiari e comuni per evitare macello:
    //
    // - ClientView:
    //   contratto implementato dalla UI.
    //   Espone i metodi che la rete usa per mostrare errori, lobby e stato partita.
    //
    // - ServerProxy:
    //   contratto usato dal controller UI per inviare comandi al server.
    //   Ha implementazioni TCP e RMI: ServerProxyTCP e ServerProxyRMI.
    //
    // - VirtualServer:
    //   contratto usato dalla rete per consegnare al client i messaggi ricevuti dal server.
    //   In TCP legge i messaggi dalla socket tramite VirtualServerTCP.
    //   In RMI riceve callback remote tramite VirtualServerRMI.
    //
    // Quindi in generale il flusso da avere in testa è questo:
    // controller UI -> ServerProxy -> server
    // server -> VirtualServer -> ClientView

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        ClientView clientView = chooseClientView(scanner);
        ServerProxy serverProxy = chooseServerProxy(scanner, clientView);

        startClient(clientView, serverProxy);
    }

    private static ClientView chooseClientView(Scanner scanner) {
        while (true) {
            System.out.println("Select UI mode:");
            System.out.println("1) GUI");
            System.out.println("2) TUI");

            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                return new GUIController();
            }
            if ("2".equals(choice)) {
                return new TuiController(scanner, System.out);
            }

            System.out.println("Invalid UI mode. Please enter 1 or 2.\n");
        }
    }

    private static ServerProxy chooseServerProxy(Scanner scanner, ClientView clientView) {
        while (true) {
            System.out.println("Select network technology:");
            System.out.println("1) TCP");
            System.out.println("2) RMI");

            String choice = scanner.nextLine().trim();
            try {
                if ("1".equals(choice)) {
                    return new ServerProxyTCP(DEFAULT_HOST, DEFAULT_TCP_PORT, clientView);
                }
                if ("2".equals(choice)) {
                    String registryUrl = "rmi://" + DEFAULT_HOST + ":" + DEFAULT_RMI_PORT + "/MesosServer";
                    RmiConnectionService connectionService = (RmiConnectionService) Naming.lookup(registryUrl);
                    return new ServerProxyRMI(connectionService, clientView);
                }
                System.out.println("Invalid network mode. Please enter 1 or 2.\n");
            } catch (Exception exception) {
                System.out.println("Failed to connect to the server: " + exception.getMessage());
            }
        }
    }

    private static void startClient(ClientView clientView, ServerProxy serverProxy) {
        if (clientView instanceof TuiController tuiController) {
            tuiController.attachServerProxy(serverProxy);
            tuiController.start();
            return;
        }

        if (clientView instanceof GUIController guiController) {
            guiController.attachServerProxy(serverProxy);
            guiController.start();
        }
    }
}
