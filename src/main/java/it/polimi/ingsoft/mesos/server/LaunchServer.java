package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServices;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServicesFactory;
import it.polimi.ingsoft.mesos.server.network.ConnectionAcceptor;
import it.polimi.ingsoft.mesos.server.virtualview.RmiSessionFactory;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;

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

            //ServerServices serverServices = ServerServicesFactory.create(ServerRuntimeConfig.fromEnvironment(environment));
            //ServerController serverController = new ServerController(serverServices.rankingService());
            ServerController serverController = new ServerController();
            Thread serverControllerThread = new Thread(serverController, "mesos-server-controller");
            serverControllerThread.start();
            int rmiPort = resolveRmiPort(environment);
            startRmiServer(serverController, rmiPort, out);


            int tcpPort = resolveTcpPort(environment);
            ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, serverController);
            Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
            acceptorThread.start();
            out.println("TCP listener attivo sulla porta " + tcpPort);
            //if (serverServices.rankingService() == null) {
            //    out.println("Modalita no-db attiva: ranking persistence disabilitata.");
            //}

            out.println("Server Mesos avviato con successo.");
            out.println("In attesa di giocatori...");
            return 0;

        } catch (Exception exception) {
            err.println("Avvio server fallito.");
            exception.printStackTrace(err);
            return 1;
        }
    }

    private static void startRmiServer(ServerController controller, int port, PrintStream out) throws Exception {
        RmiConnectionService rmiFactory = new RmiSessionFactory(controller);
        LocateRegistry.createRegistry(port);
        Registry registry = LocateRegistry.getRegistry(port);
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
