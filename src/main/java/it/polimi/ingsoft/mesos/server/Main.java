package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServices;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServicesFactory;


import java.io.PrintStream;
import java.util.Map;


/**
 * Command-line entry point for the Mesos server runtime.
 *
 * <p>The bootstrap loads runtime configuration, initializes the selected service set, verifies
 * that ranking access is available, and prints a concise startup summary for operators.
 */

public final class Main {
    /**
     * Prevents instantiation of the utility entry-point class.
     */
    private Main() {
    }

    /**
     * Starts the server runtime using environment-backed configuration.
     *
     * @param args command-line arguments currently ignored by the bootstrap
     */
    public static void main(String[] args) {
        int exitCode = run(System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {
            ServerRuntimeConfig runtimeConfig = ServerRuntimeConfig.fromEnvironment(environment);
            ServerServices serverServices = ServerServicesFactory.create(runtimeConfig);
            RankingService rankingService = serverServices.rankingService();

            int leaderboardEntries = rankingService.getLeaderboard().size();
            out.println("Server Mesos avviato.");
            if (runtimeConfig.dbEnabled()) {
                out.println("PostgreSQL raggiungibile su " + runtimeConfig.jdbcUrl());
            } else {
                out.println("Modalita no-db attiva: persistenza ranking e save-match disabilitate.");
            }
            out.println("Leaderboard entries correnti: " + leaderboardEntries);
            return 0;
        } catch (RuntimeException exception) {
            err.println("Avvio server fallito: impossibile inizializzare i servizi runtime.");
            err.println(exception.getMessage());
            return 1;
        }
    }
}
