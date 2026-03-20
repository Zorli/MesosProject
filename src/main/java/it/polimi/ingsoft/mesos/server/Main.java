package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;

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
        try {
            ServerRuntimeConfig runtimeConfig = ServerRuntimeConfig.fromEnvironment(System.getenv());
            ServerServices serverServices = ServerServicesFactory.create(runtimeConfig);
            RankingService rankingService = serverServices.rankingService();

            int leaderboardEntries = rankingService.getLeaderboard().size();
            System.out.println("Server Mesos avviato.");
            if (runtimeConfig.dbEnabled()) {
                System.out.println("PostgreSQL raggiungibile su " + runtimeConfig.jdbcUrl());
            } else {
                System.out.println("Modalita no-db attiva: persistenza ranking e save-match disabilitate.");
            }
            System.out.println("Leaderboard entries correnti: " + leaderboardEntries);
        } catch (RuntimeException exception) {
            System.err.println("Avvio server fallito: impossibile inizializzare i servizi runtime.");
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }
}
