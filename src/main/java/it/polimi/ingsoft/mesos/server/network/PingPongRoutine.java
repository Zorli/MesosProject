package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry.SessionSnapshot;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PingPongRoutine {
    private static final long PING_INTERVAL_SECONDS = 10;

    private final ServerController serverController;
    private final ScheduledExecutorService scheduler;

    public PingPongRoutine(ServerController serverController) {
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        /*
        Fancy way, instead of
          while (...) {
              tick();
              sleep(10s);
          }
         */
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ping-pong-routine");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        this.scheduler.scheduleAtFixedRate(
                this::tick,
                PING_INTERVAL_SECONDS,
                PING_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        this.scheduler.shutdownNow();
    }

    private void tick() {
        for (SessionSnapshot session : this.serverController.getSessionRegistry().snapshots()) {
            if (session.timedOut()) {
                System.out.println("Disconnected session " + session.sessionId());
                this.serverController.handleConnectionTimeout(session.sessionId());
                continue;
            }

            session.connection().sendPing();
        }
    }
}
