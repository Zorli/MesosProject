package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingService;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresRankingSchemaModule;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            ServerRuntimeConfig runtimeConfig = ServerRuntimeConfig.fromEnvironment(System.getenv());
            PostgresDatabaseBootstrap.getInstance().ensureReady(
                    runtimeConfig,
                    java.util.List.of(PostgresRankingSchemaModule.getInstance())
            );
            RankingService rankingService = new PostgresRankingService(
                    runtimeConfig.toPostgresConnectionConfig(),
                    runtimeConfig.maxBusyRetries()
            );

            int leaderboardEntries = rankingService.getLeaderboard().size();
            System.out.println("Server Mesos avviato.");
            System.out.println("PostgreSQL raggiungibile su " + runtimeConfig.jdbcUrl());
            System.out.println("Leaderboard entries correnti: " + leaderboardEntries);
        } catch (RuntimeException exception) {
            System.err.println("Avvio server fallito: impossibile inizializzare PostgreSQL.");
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }
}
