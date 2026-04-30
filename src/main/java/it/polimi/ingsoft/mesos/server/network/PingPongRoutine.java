package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry.SessionSnapshot;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PingPongRoutine {
    private static final long PING_INTERVAL_SECONDS = 10;

    private final ServerSessionRegistry sessions;
    private final ScheduledExecutorService scheduler;
    private final TimeoutHandler timeoutHandler;

    public PingPongRoutine(ServerSessionRegistry sessions, TimeoutHandler timeoutHandler) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.timeoutHandler = Objects.requireNonNull(timeoutHandler, "timeoutHandler cannot be null");
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
            if (session.user() == null) {
                continue;
            }
            if (session.timedOut()) {
                System.out.println("Disconnected User" + session.user().toString());
                this.timeoutHandler.handle(session.sessionId());
                continue;
            }

            session.connection().sendPing();
        }
    }

    public interface TimeoutHandler {
        void handle(String sessionId);
    }
}
