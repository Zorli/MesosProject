package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.ConnectionAcceptor;
import java.io.PrintStream;
import java.util.Map;


//From this class we should create all the server
//Client → TCP → VirtualView.receiveCmd()
//             → GameController.processCmd()
//             → Model.evolve()
//             → Observer notify → VirtualView.sendUpdate()
//             → TCP → Client

//here we should bootstrap the server
//1) One thread that is used to accept connection on the canonical port and instatiate virtual views
//2) The singleton servercontroller that handles the pre match setup

public final class LaunchServer {
    private static final int DEFAULT_TCP_PORT = 12_345;

    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {
            int port = resolveTcpPort(environment);
            ServerController serverController = new ServerController();
            ConnectionAcceptor acceptor = new ConnectionAcceptor(port, serverController);
            Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
            acceptorThread.start();

            out.println("Server Mesos avviato.");
            out.println("TCP listener attivo sulla porta " + port);
            return 0;
        } catch (RuntimeException exception) {
            err.println("Avvio server fallito.");
            err.println(exception.getMessage());
            return 1;
        }
    }

    private static int resolveTcpPort(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT_TCP_PORT;
        }

        String rawPort = environment.get("MESOS_TCP_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_TCP_PORT;
        }

        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MESOS_TCP_PORT must be an integer", exception);
        }
    }
}
