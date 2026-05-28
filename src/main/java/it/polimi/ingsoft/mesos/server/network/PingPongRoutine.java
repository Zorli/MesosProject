package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry.SessionSnapshot;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodic server-side keep-alive routine for registered client connections.
 * <p>
 * This class is part of the network lifecycle layer. It does not know lobby or
 * game rules: its only responsibility is to periodically inspect the connection
 * snapshots stored in {@link ServerSessionRegistry}, send ping messages to
 * still-alive sessions, and report timed-out sessions to
 * {@link ServerConnectionLifecycle}.
 * <p>
 * The routine runs on a single daemon scheduler thread. This keeps the timeout
 * check independent from controller queues and socket-reading threads, while
 * still delegating actual disconnection cleanup to the lifecycle service that
 * knows how to route the event according to the current session phase.
 */
public class PingPongRoutine {
    private static final long PING_INTERVAL_SECONDS = 5;

    private final ServerSessionRegistry sessions;
    private final ServerConnectionLifecycle connectionLifecycle;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a ping-pong routine over the shared session registry.
     * <p>
     * The registry is used to read immutable session snapshots, while the
     * lifecycle service is called when a snapshot is already timed out.
     *
     * @param sessions registry containing the currently active sessions
     * @param connectionLifecycle service responsible for timeout disconnection
     *                            handling
     * @throws NullPointerException if {@code sessions} or
     *                              {@code connectionLifecycle} is null
     */
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

    /**
     * Starts the periodic keep-alive task.
     * <p>
     * The first check is executed after {@value #PING_INTERVAL_SECONDS} seconds,
     * then repeated with the same interval. Each execution sends pings to active
     * sessions and handles sessions that already exceeded the registry timeout.
     */
    public void start() {
        this.scheduler.scheduleAtFixedRate(
                this::tick,
                PING_INTERVAL_SECONDS,
                PING_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Stops the scheduler immediately.
     * <p>
     * This method interrupts pending routine work and prevents future ping-pong
     * checks from being scheduled.
     */
    public void stop() {
        this.scheduler.shutdownNow();
    }

    /**
     * Executes one keep-alive pass over all registered session snapshots.
     * <p>
     * A timed-out session is reported to {@link ServerConnectionLifecycle}, which
     * closes the connection and starts the phase-aware disconnection flow. A
     * session that is not timed out receives a ping through its
     * {@link ClientConnection}.
     */
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
