package it.polimi.ingsoft.mesos.server.controller.match;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-match runtime that serializes all commands targeting one game.
 *
 * <p>Caller threads enqueue work and receive a future. The actual mutation runs on a dedicated
 * worker for this match, so the underlying {@link GameOrchestrator} and domain aggregate do not
 * need to be thread-safe.
 */
public final class MatchRuntime implements AutoCloseable {
    private final int gameId;
    private final GameOrchestrator orchestrator;
    private final ExecutorService executor;
    private final ConcurrentHashMap<Runnable, CompletableFuture<?>> queuedTasks;
    private final AtomicBoolean closed;

    /**
     * Creates a generic runtime identified by game id. This constructor is useful for tests and
     * infrastructure-level command submission that does not need an orchestrator.
     *
     * @param gameId game identifier
     */
    public MatchRuntime(int gameId) {
        this(gameId, null);
    }

    /**
     * Creates a runtime around one authoritative game orchestrator.
     *
     * @param orchestrator orchestrator owned by this runtime
     */
    public MatchRuntime(GameOrchestrator orchestrator) {
        this(Objects.requireNonNull(orchestrator, "Game orchestrator cannot be null").getGame().getId(), orchestrator);
    }

    private MatchRuntime(int gameId, GameOrchestrator orchestrator) {
        this.gameId = gameId;
        this.orchestrator = orchestrator;
        this.queuedTasks = new ConcurrentHashMap<>();
        this.closed = new AtomicBoolean(false);
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "match-" + gameId);
            return thread;
        });
    }

    /**
     * Returns the game identifier served by this runtime.
     *
     * @return game id
     */
    public int getGameId() {
        return this.gameId;
    }

    /**
     * Returns the wrapped orchestrator.
     *
     * @return match orchestrator
     */
    public GameOrchestrator getOrchestrator() {
        return this.requireOrchestrator();
    }

    /**
     * Enqueues a command on the match worker.
     *
     * @param command command to execute
     * @return future completed with the command result
     * @param <T> result type
     */
    public <T> CompletableFuture<T> submit(Callable<T> command) {
        Objects.requireNonNull(command, "Command cannot be null");
        if (this.closed.get()) {
            throw new MatchRuntimeClosedException(this.gameId);
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                result.complete(command.call());
            } catch (Throwable exception) {
                result.completeExceptionally(exception);
            }
        };

        Runnable trackedTask = new TrackedCommand(task);
        this.queuedTasks.put(trackedTask, result);
        try {
            this.executor.execute(trackedTask);
        } catch (RejectedExecutionException exception) {
            this.queuedTasks.remove(trackedTask);
            result.completeExceptionally(exception);
            throw new MatchRuntimeClosedException(this.gameId);
        }
        return result;
    }

    /**
     * Runs match setup and returns the updated payload.
     *
     * @return future containing updated payload
     */
    public CompletableFuture<GamePayload> setupMatch() {
        return this.submit(() -> {
            this.requireOrchestrator().setupMatch();
            return this.requireOrchestrator().getCurrentPayload();
        });
    }

    /**
     * Runs one totem placement and returns the updated payload.
     *
     * @param player acting player
     * @param tile target tile code
     * @return future containing updated payload
     */
    public CompletableFuture<GamePayload> moveTotem(Player player, String tile) {
        return this.submit(() -> {
            this.requireOrchestrator().moveTotem(player, tile);
            return this.requireOrchestrator().getCurrentPayload();
        });
    }

    /**
     * Runs one standard draw and returns the updated payload.
     *
     * @param player acting player
     * @param card selected card
     * @return future containing updated payload
     */
    public CompletableFuture<GamePayload> chooseCard(Player player, Card card) {
        return this.submit(() -> {
            this.requireOrchestrator().chooseCard(player, card);
            return this.requireOrchestrator().getCurrentPayload();
        });
    }

    /**
     * Runs one extra draw and returns the updated payload.
     *
     * @param player acting player
     * @param card selected card
     * @return future containing updated payload
     */
    public CompletableFuture<GamePayload> extraDrawn(Player player, Card card) {
        return this.submit(() -> {
            this.requireOrchestrator().extraDrawn(player, card);
            return this.requireOrchestrator().getCurrentPayload();
        });
    }

    /**
     * Resolves round effects and returns the updated payload.
     *
     * @return future containing updated payload
     */
    public CompletableFuture<GamePayload> resolveEffects() {
        return this.submit(() -> {
            this.requireOrchestrator().resolveEffects();
            return this.requireOrchestrator().getCurrentPayload();
        });
    }

    /**
     * Resets the board and returns the updated payload.
     *
     * @return future containing updated payload
     */
    public CompletableFuture<GamePayload> resetBoard() {
        return this.submit(() -> {
            this.requireOrchestrator().resetBoard();
            return this.requireOrchestrator().getCurrentPayload();
        });
    }

    /**
     * Calculates final score and returns both final payload and optional ranking update.
     *
     * @return future containing match completion result
     */
    public CompletableFuture<MatchCompletionResult> calculateFinalScore() {
        return this.submit(() -> {
            Optional<RankingResult> rankingResult = this.requireOrchestrator().calculateFinalScore();
            return new MatchCompletionResult(this.requireOrchestrator().getCurrentPayload(), rankingResult);
        });
    }

    /**
     * Returns the current payload through the serialized runtime.
     *
     * @return future containing current payload
     */
    public CompletableFuture<GamePayload> getCurrentPayload() {
        return this.submit(() -> this.requireOrchestrator().getCurrentPayload());
    }

    /**
     * Closes the runtime and rejects future submissions.
     */
    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        List<Runnable> notStarted = this.executor.shutdownNow();
        for (Runnable task : notStarted) {
            CompletableFuture<?> future = this.queuedTasks.remove(task);
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private GameOrchestrator requireOrchestrator() {
        if (this.orchestrator == null) {
            throw new IllegalStateException("Match runtime has no game orchestrator");
        }
        return this.orchestrator;
    }

    private final class TrackedCommand implements Runnable {
        private final Runnable delegate;

        private TrackedCommand(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                MatchRuntime.this.queuedTasks.remove(this);
                this.delegate.run();
            } finally {
                MatchRuntime.this.queuedTasks.remove(this);
            }
        }
    }
}
