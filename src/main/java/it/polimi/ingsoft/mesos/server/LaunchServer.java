package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.ConnectionAcceptor;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualRmiViewFactory;
import it.polimi.ingsoft.mesos.shared.RmiLoginFacade;

import java.io.PrintStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;

// From this class we bootstrap the server for BOTH TCP and RMI.
// The Singleton ServerController is shared between both network layers,
// ensuring all players play in the same lobbies regardless of their protocol.
public final class LaunchServer {
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {
            // 1. IL CUORE DEL GIOCO: Unico e condiviso tra tutti i giocatori
            ServerController serverController = new ServerController();

            // 2. AVVIO RMI: Creiamo il Registry e pubblichiamo il Portinaio
            int rmiPort = resolveRmiPort(environment);
            startRmiServer(serverController, rmiPort, out);

            // 3. AVVIO TCP: Avviamo il thread in ascolto sui socket
            int tcpPort = resolveTcpPort(environment);
            ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, serverController);
            Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
            acceptorThread.start();
            out.println("TCP listener attivo sulla porta " + tcpPort);

            out.println("Server Mesos avviato con successo.");
            out.println("In attesa di giocatori...");
            return 0;

        } catch (Exception exception) {
            // Usiamo Exception generica perché startRmiServer lancia eccezioni RMI
            err.println("Avvio server fallito.");
            exception.printStackTrace(err); // <-- Stampa lo stacktrace utile per debug
            return 1;
        }
    }

    private static void startRmiServer(ServerController controller, int port, PrintStream out) throws Exception {
        // PRO-TIP: Se testerete il gioco su due PC diversi, scommenta questa riga
        // e inserisci l'indirizzo IP del PC su cui gira il server (es. "192.168.1.50").
        // Altrimenti RMI potrebbe inviare al client "localhost", bloccando la connessione esterna.
        // System.setProperty("java.rmi.server.hostname", "INDIRIZZO_IP_SERVER");

        // 1. Creiamo il Portinaio
        RmiLoginFacade rmiFactory = new VirtualRmiViewFactory(controller);

        // 2. Creiamo il Registry direttamente in Java (sulla porta 1099)
        LocateRegistry.createRegistry(port);
        Registry registry = LocateRegistry.getRegistry(port);

        // 3. Lo leghiamo al nome "MesosServer"
        registry.rebind("MesosServer", rmiFactory);

        out.println("RMI listener attivo sulla porta " + port);
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

    private static int resolveRmiPort(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT_RMI_PORT;
        }
        String rawPort = environment.get("MESOS_RMI_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_RMI_PORT;
        }
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MESOS_RMI_PORT must be an integer", exception);
        }
    }
}