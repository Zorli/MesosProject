package it.polimi.ingsoft.mesos.server.controller.match;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of currently active match runtimes.
 */
public final class MatchRegistry {
    private final ConcurrentHashMap<Integer, MatchRuntime> runtimes = new ConcurrentHashMap<>();

    /**
     * Registers one active match runtime.
     *
     * @param runtime runtime to register
     */
    public void register(MatchRuntime runtime) {
        Objects.requireNonNull(runtime, "Match runtime cannot be null");
        MatchRuntime previous = this.runtimes.putIfAbsent(runtime.getGameId(), runtime);
        if (previous != null) {
            throw new MatchAlreadyRegisteredException(runtime.getGameId());
        }
    }

    /**
     * Finds an active runtime by game id.
     *
     * @param gameId game identifier
     * @return optional runtime
     */
    public Optional<MatchRuntime> find(int gameId) {
        return Optional.ofNullable(this.runtimes.get(gameId));
    }

    /**
     * Returns an active runtime or fails if missing.
     *
     * @param gameId game identifier
     * @return registered runtime
     */
    public MatchRuntime require(int gameId) {
        MatchRuntime runtime = this.runtimes.get(gameId);
        if (runtime == null) {
            throw new MatchNotFoundException(gameId);
        }
        return runtime;
    }

    /**
     * Removes one runtime without closing it.
     *
     * @param gameId game identifier
     * @return removed runtime if present
     */
    public Optional<MatchRuntime> remove(int gameId) {
        return Optional.ofNullable(this.runtimes.remove(gameId));
    }

    /**
     * Removes and closes one runtime. Missing matches are ignored.
     *
     * @param gameId game identifier
     */
    public void closeMatch(int gameId) {
        this.remove(gameId).ifPresent(MatchRuntime::close);
    }

    /**
     * Closes and removes all active runtimes.
     */
    public void closeAll() {
        this.runtimes.forEach((gameId, runtime) -> runtime.close());
        this.runtimes.clear();
    }
}
