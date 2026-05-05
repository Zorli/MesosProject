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

public final class LaunchServer {
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
    /*
    Architettura del server:
    - Virtualview RMI/TCP: Gestione delle connessioni in entrata ed in uscita, si occupano del relativo protocollo di rete
      scambiandosi payload. Quello che la virtualview sa fare è ricevere e mandare in uscita payload, sul come instradarli
      internamente se ne occupa il relativo router
    - Harness vari protocol dependent delle virtualview : connection acceptor, commandhandler, etc
    - Command Router: Dato un payload recuper lo snapshot della sessione del player e lo instrada correttamente
    //TODO : Finire lo spiegone architetturale
    */
    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {

            //ServerServices serverServices = ServerServicesFactory.create(ServerRuntimeConfig.fromEnvironment(environment));
            //ServerController serverController = new ServerController(serverServices.rankingService());
            ServerController serverController = new ServerController();
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
