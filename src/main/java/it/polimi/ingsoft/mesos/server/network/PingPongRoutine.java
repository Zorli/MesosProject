package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry.SessionSnapshot;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PingPongRoutine {
    private static final long PING_INTERVAL_SECONDS = 5;

    private final ServerSessionRegistry sessions;
    private final ServerConnectionLifecycle connectionLifecycle;
    private final ScheduledExecutorService scheduler;

    public PingPongRoutine(ServerSessionRegistry sessions, ServerConnectionLifecycle connectionLifecycle) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.connectionLifecycle = Objects.requireNonNull(connectionLifecycle, "connectionLifecycle cannot be null");
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
        for (SessionSnapshot session : this.sessions.snapshots()) {
            if (session.timedOut()) {
                System.out.println("Disconnected session " + session.sessionId());
                this.connectionLifecycle.handleConnectionTimeout(session.sessionId());
                continue;
            }

            session.connection().sendPing();
        }
    }
}
